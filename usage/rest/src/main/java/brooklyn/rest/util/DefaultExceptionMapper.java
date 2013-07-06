package brooklyn.rest.util;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

import brooklyn.rest.domain.ApiError;

@Provider
public class DefaultExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultExceptionMapper.class);

    /**
     * Maps a throwable to a response.
     * <p/>
     * Returns {@link WebApplicationException#getResponse} if the exception is an instance of
     * {@link WebApplicationException}. Otherwise maps known exceptions to responses. If no
     * mapping is found a {@link Status#INTERNAL_SERVER_ERROR} is assumed.
     */
    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException) {
            WebApplicationException wae = (WebApplicationException) exception;
            return wae.getResponse();
        }

        // Assume ClassCastExceptions are caused by TypeCoercions from input paramters gone wrong.
        if (exception instanceof ClassCastException) {
            return Response.status(Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ApiError(exception.getMessage()))
                .build();
        }

        LOG.info("No exception mapping for " + exception.getClass() + ", responding 500", exception);
        String message = Optional.fromNullable(exception.getMessage())
                .or("Internal error. Check server logs for details.");
        return Response.status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ApiError(message))
                .build();
    }

}
