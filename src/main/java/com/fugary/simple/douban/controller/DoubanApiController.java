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
import jakarta.ws.rs.core.Response;
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
        String url = doubanApiConfigProperties.searchUrl()
                .replace("{searchType}", catType)
                .replace("{searchText}", searchText);

        String resultStr = client.target(url)
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
        String url = urlTemplate.replace("{id}", id).replace("{isbn}", id);
        ResultVo resultVo = new ResultVo();
        BookVo bookVo = bookLoader.loadBook(url);
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

            String template = builder.toTemplate(); // kept for checking against current, but maybe safer to check if it already contains the proxy path
            // Better check: does it look like our proxy URL?
            // Actually, the intent of original code was: "if the image URL is NOT already pointing to OUR proxy, then rewrite it".
            // The original code used builder.toUriString() which resolves placeholders.
            // Here we don't have placeholders in builder yet.

            String currentProxyBase = builder.build().toString();

            if (!bookVo.getImage().startsWith(currentProxyBase)) {
                String coverUrl = builder.queryParam("cover", bookVo.getImage()).build().toString();
                bookVo.setImage(coverUrl);
            }
        }
    }
}
