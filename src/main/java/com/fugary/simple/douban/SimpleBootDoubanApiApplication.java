package com.fugary.simple.douban;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class SimpleBootDoubanApiApplication {

    public static void main(String[] args) {
        Quarkus.run(args);
    }

}
