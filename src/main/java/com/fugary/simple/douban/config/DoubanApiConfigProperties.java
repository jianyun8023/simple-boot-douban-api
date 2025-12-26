package com.fugary.simple.douban.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.*;

/**
 * Created on 2021/8/17 19:23 .<br>
 *
 * @author gary.fu
 */
@ConfigMapping(prefix = "douban.api")
public interface DoubanApiConfigProperties {

    Map<String, String> mappings();

    @WithDefault("3")
    int count();

    String baseUrl();

    String searchUrl();

    String detailUrl();

    String isbnUrl();

    @WithDefault("true")
    boolean proxyImageUrl();

}
