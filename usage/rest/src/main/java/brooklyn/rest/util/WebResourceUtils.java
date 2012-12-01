package brooklyn.rest.util;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.rest.domain.ApiError;

public class WebResourceUtils {

    private static final Logger log = LoggerFactory.getLogger(WebResourceUtils.class);
    
    public static WebApplicationException notFound(String format, Object... args) {
        String msg = String.format(format, args);
        if (log.isInfoEnabled()) log.info("returning 404 notFound("+msg+")");
        throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(msg)).build());
    }

    public static WebApplicationException preconditionFailed(String format, Object... args) {
        String msg = String.format(format, args);
        if (log.isInfoEnabled()) log.info("returning 412 preconditionFailed("+msg+")");
        throw new WebApplicationException(Response.status(Response.Status.PRECONDITION_FAILED)
                .entity(new ApiError(msg)).build());
    }

}
