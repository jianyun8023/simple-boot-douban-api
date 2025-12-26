package com.fugary.simple.douban.util;

import org.apache.commons.lang3.StringUtils;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.enterprise.inject.spi.CDI;
import io.vertx.core.http.HttpServerRequest;

/**
 * @author Gary Fu
 * @date 2021/8/28 11:09
 */
public class HttpRequestUtils {

    private HttpRequestUtils() {
    }

    /**
     * 获取Request
     *
     * @return
     */
    public static HttpServerRequest getCurrentRequest() {
        try {
            return CDI.current().select(HttpServerRequest.class).get();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取HEADER
     *
     * @param key
     * @return
     */
    public static String getHeader(String key) {
        HttpServerRequest currentRequest = getCurrentRequest();
        if (currentRequest != null) {
            return currentRequest.getHeader(key);
        }
        return null;
    }

    /**
     * 获取UserAgent,如果为空给默认值
     *
     * @return
     */
    public static String getUserAgent() {
        String userAgent = getHeader(HttpHeaders.USER_AGENT);
        if (StringUtils.isBlank(userAgent)) {
            userAgent = "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3573.0 Safari/537.36";
        }
        return userAgent;
    }

    /**
     * 计算schema
     *
     * @return
     */
    public static String getSchema() {
        HttpServerRequest currentRequest = getCurrentRequest();
        String schema = StringUtils.EMPTY;
        if (currentRequest != null) {
            schema = currentRequest.getHeader("x-forwarded-proto");
            if (StringUtils.isBlank(schema)) {
                schema = currentRequest.scheme();
            }
        }
        if (StringUtils.isBlank(schema)) {
            schema = "http";
        }
        return schema;
    }
}
