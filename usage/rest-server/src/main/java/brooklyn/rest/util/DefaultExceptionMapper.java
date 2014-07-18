/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.rest.util;

import java.util.Set;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.error.YAMLException;

import brooklyn.management.entitlement.Entitlements;
import brooklyn.rest.domain.ApiError;
import brooklyn.rest.domain.ApiError.Builder;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.UserFacingException;
import brooklyn.util.flags.ClassCoercionException;
import brooklyn.util.text.Strings;

@Provider
public class DefaultExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultExceptionMapper.class);

    static Set<Class<?>> warnedUnknownExceptions = MutableSet.of();
    
    /**
     * Maps a throwable to a response.
     * <p/>
     * Returns {@link WebApplicationException#getResponse} if the exception is an instance of
     * {@link WebApplicationException}. Otherwise maps known exceptions to responses. If no
     * mapping is found a {@link Status#INTERNAL_SERVER_ERROR} is assumed.
     */
    @Override
    public Response toResponse(Throwable throwable) {

        LOG.debug("REST request running as {} threw: {}", Entitlements.getEntitlementContext(), throwable);
        if (LOG.isTraceEnabled()) {
            LOG.trace("Full details of "+Entitlements.getEntitlementContext()+" "+throwable, throwable);
        }

        // Some methods will throw this, which gets converted automatically
        if (throwable instanceof WebApplicationException) {
            WebApplicationException wae = (WebApplicationException) throwable;
            return wae.getResponse();
        }

        // The nicest way for methods to provide errors, wrap as this, and the stack trace will be suppressed
        if (throwable instanceof UserFacingException) {
            return ApiError.of(throwable.getMessage()).asBadRequestResponseJson();
        }

        // For everything else, a trace is supplied
        
        // Assume ClassCoercionExceptions are caused by TypeCoercions from input parameters gone wrong
        // And IllegalArgumentException for malformed input parameters.
        if (throwable instanceof ClassCoercionException || throwable instanceof IllegalArgumentException) {
            return ApiError.of(throwable).asBadRequestResponseJson();
        }

        // YAML exception 
        if (throwable instanceof YAMLException) {
            return ApiError.builder().message(throwable.getMessage()).prefixMessage("Invalid YAML").build().asBadRequestResponseJson();
        }

        if (!Exceptions.isPrefixBoring(throwable)) {
            if ( warnedUnknownExceptions.add( throwable.getClass() )) {
                LOG.warn("REST call generated exception type "+throwable.getClass()+" unrecognized in "+getClass()+" (subsequent occurrences will be logged debug only): " + throwable);
            }
        }
        
        Builder rb = ApiError.builderFromThrowable(throwable);
        if (Strings.isBlank(rb.getMessage()))
            rb.message("Internal error. Contact server administrator to consult logs for more details.");
        return rb.build().asResponse(Status.INTERNAL_SERVER_ERROR, MediaType.APPLICATION_JSON_TYPE);
    }

}
