package com.fugary.simple.douban.controller;

import com.fugary.simple.douban.config.DoubanApiConfigProperties;
import com.fugary.simple.douban.loader.BookLoader;
import com.fugary.simple.douban.util.DoubanUrlUtils;
import com.fugary.simple.douban.util.HttpRequestUtils;
import com.fugary.simple.douban.vo.BookVo;
import com.fugary.simple.douban.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Created on 2021/8/17 18:43 .<br>
 *
 * @author gary.fu
 */
@Slf4j
@Path("/v2/book")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class DoubanApiController {

    @Inject
    DoubanApiConfigProperties doubanApiConfigProperties;

    @Inject
    BookLoader bookLoader;

    @Context
    UriInfo uriInfo;

    @Inject
    ManagedExecutor managedExecutor;

    private Client client = ClientBuilder.newClient();

    @GET
    @Path("/search")
    public ResultVo searchBook(@QueryParam("q") String searchText) throws ExecutionException, InterruptedException {
        if (StringUtils.isBlank(searchText)) {
            throw new BadRequestException("Query parameter 'q' is required");
        }
        if (searchText.matches("\\d{10,}")) {
            return searchIsbn(searchText);
        }
        long start = System.currentTimeMillis();
        ResultVo resultVo = new ResultVo();
        resultVo.setBooks(new ArrayList<>());

        String catType = doubanApiConfigProperties.mappings().get("book");
        List<Element> bookElements = searchBookElements(searchText, catType); // 按照网页查询，应该速度稍慢
        log.info("查询列表{}条耗时{}ms", bookElements.size(), System.currentTimeMillis() - start);
        List<CompletableFuture<BookVo>> list = new ArrayList<>();

        // 多线程查询多本书籍
        bookElements.forEach(content -> {
            String href = content.attr("href");
            Map<String, String> map = DoubanUrlUtils.parseQuery(URI.create(href).getQuery());
            String url = map.get("url");
            if (DoubanUrlUtils.isBookUrl(url) && list.size() < doubanApiConfigProperties.count()) {
                list.add(CompletableFuture.supplyAsync(() -> {
                    BookVo bookVo = bookLoader.loadBook(url);
                    if (bookVo != null) {
                        resultVo.setSuccess(true);
                        processBookImage(bookVo);
                        resultVo.getBooks().add(bookVo);
                    }
                    return bookVo;
                }, managedExecutor));
            }
        });
        CompletableFuture.allOf(list.toArray(new CompletableFuture[0])).get();
        log.info("查询书籍{}条完成耗时{}ms", list.size(), System.currentTimeMillis() - start);
        return resultVo;
    }

    /**
     * 列表页从html中获取
     *
     * @param searchText
     * @param catType
     * @return
     */
    protected List<Element> searchBookElements(String searchText, String catType) {
        String resultStr = client.target(doubanApiConfigProperties.searchUrl())
                .resolveTemplate("searchType", catType)
                .resolveTemplate("searchText", searchText)
                .request()
                .header(HttpHeaders.USER_AGENT, HttpRequestUtils.getUserAgent())
                .get(String.class);

        Document doc = Jsoup.parse(resultStr);
        return doc.select("a.nbg");
    }

    @GET
    @Path("/isbn/{isbn}")
    public ResultVo searchIsbn(@PathParam("isbn") String isbn) {
        return detailResult(doubanApiConfigProperties.isbnUrl(), isbn);
    }

    @GET
    @Path("/{id}")
    public ResultVo detail(@PathParam("id") String id) {
        return detailResult(doubanApiConfigProperties.detailUrl(), id);
    }

    /**
     * 获取详情
     *
     * @param urlTemplate
     * @param id
     * @return
     */
    protected ResultVo detailResult(String urlTemplate, String id) {
        long start = System.currentTimeMillis();
        ResultVo resultVo = new ResultVo();
        // Use WebTarget templating if possible, but here we construct BookLoader
        // BookLoader takes a full URL string.
        // We need to resolve the template first.
        // We can use UriBuilder for this without making a request.

        String url = UriBuilder.fromUri(urlTemplate)
                .resolveTemplate("id", id)
                .resolveTemplate("isbn", id)
                .toTemplate(); // Wait, toTemplate returns the template string. We want the string with values.
        // UriBuilder.build() returns a URI.

        // Actually, UriBuilder.fromPath/fromUri parses templates.
        // .build(Object...) replaces them by order or map.
        // But here we have named parameters in the properties: {id} or {isbn}.
        // UriBuilder supports map for buildFromMap or just build with map.
        // But strict JAX-RS UriBuilder might not support arbitrary named params unless they are in path as {name}.
        // The URL is full URL: https://...

        // Let's stick to client.target for resolution if we were calling it directly, but here we pass the URL to loadBook.
        // loadBook uses client.target(bookUrl).
        // If we pass a template to loadBook, loadBook needs to handle it? No, loadBook expects a URL.

        // So we need to resolve the string here.
        // Using UriBuilder to resolve templates in a string:
        String resolvedUrl = UriBuilder.fromUri(urlTemplate)
                .resolveTemplate("id", id)
                .resolveTemplate("isbn", id)
                .build()
                .toString();

        BookVo bookVo = bookLoader.loadBook(resolvedUrl);
        if (bookVo != null) {
            resultVo.setSuccess(true);
            processBookImage(bookVo);
            resultVo.setBooks(Arrays.asList(bookVo));
        }
        log.info("精确查询{}耗时{}ms", id, System.currentTimeMillis() - start);
        return resultVo;
    }

    /**
     * 代理图片
     *
     * @param bookVo
     */
    protected void processBookImage(BookVo bookVo) {
        if (bookVo != null && StringUtils.isNotBlank(bookVo.getImage()) && doubanApiConfigProperties.proxyImageUrl()) {
            // Need to reconstruct current base URL
            URI baseUri = uriInfo.getBaseUri();
            UriBuilder builder = UriBuilder.fromUri(baseUri)
                    .path("/view/cover")
                    .scheme(HttpRequestUtils.getSchema()) // Try to get X-Forwarded-Proto if available
                    .host(baseUri.getHost());
            if (baseUri.getPort() != -1) {
                 builder.port(baseUri.getPort());
            }

            String currentProxyBase = builder.build().toString();

            if (!bookVo.getImage().startsWith(currentProxyBase)) {
                String coverUrl = builder.queryParam("cover", bookVo.getImage()).build().toString();
                bookVo.setImage(coverUrl);
            }
        }
    }
}
