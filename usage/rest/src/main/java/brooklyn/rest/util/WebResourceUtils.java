package brooklyn.rest.util;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import brooklyn.rest.domain.ApiError;

public class WebResourceUtils {

    public static WebApplicationException notFound(String format, Object... args) {
        throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(String.format(format, args))).build());
    }

    public static WebApplicationException preconditionFailed(String format, Object... args) {
        throw new WebApplicationException(Response.status(Response.Status.PRECONDITION_FAILED)
                .entity(new ApiError(String.format(format, args))).build());
    }

}
