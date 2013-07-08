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
import brooklyn.util.flags.ClassCoercionException;

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
    public Response toResponse(Throwable throwable) {

        if (LOG.isTraceEnabled()) {
            String message = Optional.fromNullable(throwable.getMessage()).or(throwable.getClass().getName());
            LOG.trace("Request threw: " + message);
        }

        if (throwable instanceof WebApplicationException) {
            WebApplicationException wae = (WebApplicationException) throwable;
            return wae.getResponse();
        }

        // Assume ClassCoercionExceptions are caused by TypeCoercions from input paramters gone wrong.
        if (throwable instanceof ClassCoercionException) {
            return Response.status(Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(ApiError.builder()
                        .message(throwable.getMessage())
                        .build())
                .build();
        }

        LOG.info("No exception mapping for " + throwable.getClass() + ", responding 500", throwable);
        String message = Optional.fromNullable(throwable.getMessage())
                .or("Internal error. Check server logs for details.");
        return Response.status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(ApiError.fromThrowable(throwable)
                        .message(message)
                        .build())
                .build();
    }

}
