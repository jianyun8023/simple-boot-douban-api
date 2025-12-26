package com.fugary.simple.douban.controller;

import com.fugary.simple.douban.loader.BookLoader;
import lombok.extern.slf4j.Slf4j;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Created on 2021/10/15 15:47 .<br>
 *
 * @author gary.fu
 */
@Path("/")
@ApplicationScoped
@Slf4j
public class DoubanImageController {

    @Inject
    BookLoader bookLoader;

    @GET
    @Path("/view/cover")
    @Produces("image/jpeg")
    public byte[] viewImage(@QueryParam("cover") String coverUrl) {
        byte[] resultBytes = bookLoader.loadImage(coverUrl);
        if (resultBytes != null) {
            return resultBytes;
        }
        return new byte[0];
    }
}
