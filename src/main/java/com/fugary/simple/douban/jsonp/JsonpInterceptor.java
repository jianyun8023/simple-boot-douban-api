package com.fugary.simple.douban.jsonp;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import java.io.IOException;
import java.util.regex.Pattern;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
public class JsonpInterceptor implements WriterInterceptor {

    private static final Pattern CALLBACK_PARAM_PATTERN = Pattern.compile("[0-9A-Za-z_.]*");
    private static final String CALLBACK_PARAM = "callback";

    @Context
    UriInfo uriInfo;

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
        String callback = uriInfo.getQueryParameters().getFirst(CALLBACK_PARAM);
        if (callback != null && !callback.isEmpty() && CALLBACK_PARAM_PATTERN.matcher(callback).matches()) {
            context.getOutputStream().write((callback + "(").getBytes());
            context.proceed();
            context.getOutputStream().write(");".getBytes());
            context.setMediaType(jakarta.ws.rs.core.MediaType.valueOf("application/javascript"));
        } else {
            context.proceed();
        }
    }
}
