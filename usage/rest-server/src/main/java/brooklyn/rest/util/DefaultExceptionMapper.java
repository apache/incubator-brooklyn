/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
package brooklyn.rest.util;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.error.YAMLException;

import com.google.common.base.Optional;

import brooklyn.rest.domain.ApiError;
import brooklyn.rest.domain.ApiError.Builder;
import brooklyn.util.flags.ClassCoercionException;
import brooklyn.util.text.Strings;

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

        // Assume ClassCoercionExceptions are caused by TypeCoercions from input parameters gone wrong.
        if (throwable instanceof ClassCoercionException)
            return responseBadRequestJson(ApiError.of(throwable));

        if (throwable instanceof YAMLException)
            return responseBadRequestJson(ApiError.builderFromThrowable(throwable).prefixMessage("Invalid YAML", ": ").build());
        
        LOG.info("No exception mapping for " + throwable.getClass() + ", responding 500", throwable);
        Builder rb = ApiError.builderFromThrowable(throwable);
        if (Strings.isBlank(rb.getMessage()))
            rb.message("Internal error. Check server logs for details.");
        return Response.status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(rb.build())
                .build();
    }

    private Response responseBadRequestJson(ApiError build) {
        return Response.status(Status.BAD_REQUEST)
            .type(MediaType.APPLICATION_JSON)
            .entity(build)
            .build();
    }

}
