package com.fugary.simple.douban.loader;

import com.fugary.simple.douban.config.DoubanApiConfigProperties;
import com.fugary.simple.douban.provider.BookHtmlParseProvider;
import com.fugary.simple.douban.util.HttpRequestUtils;
import com.fugary.simple.douban.vo.BookVo;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import jakarta.ws.rs.core.HttpHeaders;

/**
 * @author Gary Fu
 * @date 2021/8/28 11:02
 */
@ApplicationScoped
@Slf4j
public class DoubanBookLoaderImpl implements BookLoader {

    @Inject
    BookHtmlParseProvider bookHtmlParseProvider;

    @Inject
    DoubanApiConfigProperties doubanApiConfigProperties;

    private Client client = ClientBuilder.newClient();

    @CacheResult(cacheName = "dobanBook")
    @Override
    public BookVo loadBook(String bookUrl) {
        String bookStr = client.target(bookUrl)
                .request()
                .header(HttpHeaders.USER_AGENT, HttpRequestUtils.getUserAgent())
                .header("Referer", doubanApiConfigProperties.baseUrl())
                .get(String.class);
        return bookHtmlParseProvider.parse(bookUrl, bookStr);
    }

    @CacheResult(cacheName = "doubanImage")
    @Override
    public byte[] loadImage(String imageUrl) {
        try {
            Response response = client.target(imageUrl)
                    .request()
                    .header(HttpHeaders.USER_AGENT, HttpRequestUtils.getUserAgent())
                    .header("Referer", doubanApiConfigProperties.baseUrl())
                    .get();
            if (response.getStatus() == 200) {
                log.info("获取{}图片成功", imageUrl);
                return response.readEntity(byte[].class);
            }
        } catch (Exception e) {
            log.error("获取{}图片异常: {}", imageUrl, e.getMessage());
        }
        return null;
    }
}
