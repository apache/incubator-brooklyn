package io.brooklyn.camp.rest.util;

import io.brooklyn.camp.dto.ApiErrorDto;

import java.net.URI;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebResourceUtils {

    private static final Logger log = LoggerFactory.getLogger(WebResourceUtils.class);
    
    public static WebApplicationException notFound(String format, Object... args) {
        String msg = String.format(format, args);
        if (log.isDebugEnabled()) log.debug("returning 404 notFound("+msg+")");
        throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(ApiErrorDto.builder().message(msg).build()).build());
    }

    public static WebApplicationException preconditionFailed(String format, Object... args) {
        String msg = String.format(format, args);
        if (log.isDebugEnabled()) log.debug("returning 412 preconditionFailed("+msg+")");
        throw new WebApplicationException(Response.status(Response.Status.PRECONDITION_FAILED)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(ApiErrorDto.builder().message(msg).build()).build());
    }

    public static Response created(UriInfo info, String resourceUriPath) {
        // see http://stackoverflow.com/questions/13702481/javax-response-prepends-method-path-when-setting-location-header-path-on-status
        // for why we have to return absolute path
        URI resourceUri = info.getBaseUriBuilder().path( resourceUriPath ).build();
        return Response.created(resourceUri).build();
    }
    
}
