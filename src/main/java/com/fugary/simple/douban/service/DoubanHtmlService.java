package com.fugary.simple.douban.service;

import com.fugary.simple.douban.config.DoubanApiConfigProperties;
import com.fugary.simple.douban.util.HttpRequestUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.List;

@ApplicationScoped
public class DoubanHtmlService {

    @Inject
    DoubanApiConfigProperties doubanApiConfigProperties;

    private Client client = ClientBuilder.newClient();

    public List<Element> searchBookElements(String searchText, String catType) {
        String resultStr = client.target(doubanApiConfigProperties.searchUrl())
                .resolveTemplate("searchType", catType)
                .resolveTemplate("searchText", searchText)
                .request()
                .header(HttpHeaders.USER_AGENT, HttpRequestUtils.getUserAgent())
                .get(String.class);

        Document doc = Jsoup.parse(resultStr);
        return doc.select("a.nbg");
    }
}
