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
package org.apache.brooklyn.rest.util;

import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.rest.domain.ApiError;
import org.apache.brooklyn.rest.util.json.BrooklynJacksonJsonProvider;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.net.Urls;
import brooklyn.util.text.StringEscapes.JavaStringEscapes;

import com.google.common.collect.ImmutableMap;
import com.sun.jersey.spi.container.ContainerResponse;

public class WebResourceUtils {

    private static final Logger log = LoggerFactory.getLogger(WebResourceUtils.class);

    /** @throws WebApplicationException with an ApiError as its body and the given status as its response code. */
    public static WebApplicationException throwWebApplicationException(Response.Status status, String format, Object... args) {
        String msg = String.format(format, args);
        if (log.isDebugEnabled()) {
            log.debug("responding {} {} ({})",
                    new Object[]{status.getStatusCode(), status.getReasonPhrase(), msg});
        }
        ApiError apiError = ApiError.builder().message(msg).errorCode(status).build();
        // including a Throwable is the only way to include a message with the WebApplicationException - ugly!
        throw new WebApplicationException(new Throwable(apiError.toString()), apiError.asJsonResponse());
    }

    /** @throws WebApplicationException With code 500 internal server error */
    public static WebApplicationException serverError(String format, Object... args) {
        return throwWebApplicationException(Response.Status.INTERNAL_SERVER_ERROR, format, args);
    }

    /** @throws WebApplicationException With code 400 bad request */
    public static WebApplicationException badRequest(String format, Object... args) {
        return throwWebApplicationException(Response.Status.BAD_REQUEST, format, args);
    }

    /** @throws WebApplicationException With code 401 unauthorized */
    public static WebApplicationException unauthorized(String format, Object... args) {
        return throwWebApplicationException(Response.Status.UNAUTHORIZED, format, args);
    }

    /** @throws WebApplicationException With code 403 forbidden */
    public static WebApplicationException forbidden(String format, Object... args) {
        return throwWebApplicationException(Response.Status.FORBIDDEN, format, args);
    }

    /** @throws WebApplicationException With code 404 not found */
    public static WebApplicationException notFound(String format, Object... args) {
        return throwWebApplicationException(Response.Status.NOT_FOUND, format, args);
    }

    /** @throws WebApplicationException With code 412 precondition failed */
    public static WebApplicationException preconditionFailed(String format, Object... args) {
        return throwWebApplicationException(Response.Status.PRECONDITION_FAILED, format, args);
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

    /** as {@link #getValueForDisplay(ObjectMapper, Object, boolean, boolean)} with no mapper
     * (so will only handle a subset of types) */
    public static Object getValueForDisplay(Object value, boolean preferJson, boolean isJerseyReturnValue) {
        return getValueForDisplay(null, value, preferJson, isJerseyReturnValue);
    }
    
    /** returns an object which jersey will handle nicely, converting to json,
     * sometimes wrapping in quotes if needed (for outermost json return types);
     * if json is not preferred, this simply applies a toString-style rendering */ 
    public static Object getValueForDisplay(ObjectMapper mapper, Object value, boolean preferJson, boolean isJerseyReturnValue) {
        if (preferJson) {
            if (value==null) return null;
            Object result = value;
            // no serialization checks required, with new smart-mapper which does toString
            // (note there is more sophisticated logic in git history however)
            result = value;
            
            if (isJerseyReturnValue) {
                if (result instanceof String) {
                    // Jersey does not do json encoding if the return type is a string,
                    // expecting the returner to do the json encoding himself
                    // cf discussion at https://github.com/dropwizard/dropwizard/issues/231
                    result = JavaStringEscapes.wrapJavaString((String)result);
                }
            }
            
            return result;
        } else {
            if (value==null) return "";
            return value.toString();            
        }
    }

    public static String getPathFromVersionedId(String versionedId) {
        if (CatalogUtils.looksLikeVersionedId(versionedId)) {
            String symbolicName = CatalogUtils.getIdFromVersionedId(versionedId);
            String version = CatalogUtils.getVersionFromVersionedId(versionedId);
            return Urls.encode(symbolicName) + "/" + Urls.encode(version);
        } else {
            return Urls.encode(versionedId);
        }
    }

    /** Sets the {@link HttpServletResponse} target (last argument) from the given source {@link Response};
     * useful in filters where we might have a {@link Response} and need to set up an {@link HttpServletResponse}. 
     * Similar to {@link ContainerResponse#setResponse(Response)}; nothing like that seems to be available for {@link HttpServletResponse}. */
    public static void applyJsonResponse(ServletContext servletContext, Response source, HttpServletResponse target) throws IOException {
        target.setStatus(source.getStatus());
        target.setContentType(MediaType.APPLICATION_JSON);
        target.setCharacterEncoding("UTF-8");
        target.getWriter().write(BrooklynJacksonJsonProvider.findAnyObjectMapper(servletContext, null).writeValueAsString(source.getEntity()));
    }
}
