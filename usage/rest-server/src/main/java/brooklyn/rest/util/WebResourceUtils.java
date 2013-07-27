package brooklyn.rest.util;

import java.util.Map;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

import brooklyn.rest.domain.ApiError;
import brooklyn.util.exceptions.Exceptions;

public class WebResourceUtils {

    private static final Logger log = LoggerFactory.getLogger(WebResourceUtils.class);
    
    public static WebApplicationException notFound(String format, Object... args) {
        String msg = String.format(format, args);
        if (log.isDebugEnabled()) log.debug("returning 404 notFound("+msg+") - may be a stale browser session");
        throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(ApiError.builder().message(msg).build()).build());
    }

    public static WebApplicationException preconditionFailed(String format, Object... args) {
        String msg = String.format(format, args);
        if (log.isDebugEnabled()) log.debug("returning 412 preconditionFailed("+msg+")");
        throw new WebApplicationException(Response.status(Response.Status.PRECONDITION_FAILED)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(ApiError.builder().message(msg).build()).build());
    }

    public final static Map<String,com.google.common.net.MediaType> IMAGE_FORMAT_MIME_TYPES = ImmutableMap.<String, com.google.common.net.MediaType>builder()
            .put("jpg", com.google.common.net.MediaType.JPEG)
            .put("jpeg", com.google.common.net.MediaType.JPEG)
            .put("png", com.google.common.net.MediaType.PNG)
            .put("gif", com.google.common.net.MediaType.GIF)
            .put("svg", com.google.common.net.MediaType.SVG_UTF_8)
            .build();
    
    public static MediaType getImageMediaTypeFromExtension(String extension) {
        com.google.common.net.MediaType mime = IMAGE_FORMAT_MIME_TYPES.get(extension.toLowerCase());
        if (mime==null) return null;
        try {
            return MediaType.valueOf(mime.toString());
        } catch (Exception e) {
            log.warn("Unparseable MIME type "+mime+"; ignoring ("+e+")");
            Exceptions.propagateIfFatal(e);
            return null;
        }
    }
    
}
