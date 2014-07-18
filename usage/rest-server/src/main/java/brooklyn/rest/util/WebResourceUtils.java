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

import java.util.Map;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

import brooklyn.rest.domain.ApiError;
import brooklyn.util.collections.Jsonya;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.StringEscapes.JavaStringEscapes;

public class WebResourceUtils {

    private static final Logger log = LoggerFactory.getLogger(WebResourceUtils.class);

    /** @throws WebApplicationException With code 400 bad request */
    public static WebApplicationException badRequest(String format, Object... args) {
        String msg = String.format(format, args);
        if (log.isDebugEnabled()) log.debug("returning 400 bad request ("+msg+")");
        throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(ApiError.builder().message(msg).build()).build());
    }

    /** @throws WebApplicationException With code 401 unauthorized */
    public static WebApplicationException unauthorized(String format, Object... args) {
        String msg = String.format(format, args);
        if (log.isDebugEnabled()) log.debug("returning 401 unauthorized("+msg+")");
        throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(ApiError.builder().message(msg).build()).build());
    }

    /** @throws WebApplicationException With code 404 not found */
    public static WebApplicationException notFound(String format, Object... args) {
        String msg = String.format(format, args);
        if (log.isDebugEnabled()) log.debug("returning 404 notFound("+msg+")");
        throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(ApiError.builder().message(msg).build()).build());
    }

    /** @throws WebApplicationException With code 412 precondition failed */
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

    /** returns an object which jersey will handle nicely, converting to json,
     * sometimes wrapping in quotes if needed (for outermost json return types) */ 
    public static Object getValueForDisplay(Object value, boolean preferJson, boolean isJerseyReturnValue) {
        
        if (preferJson) {
            if (value==null) return null;
            Object result = Jsonya.convertToJsonPrimitive(value);
            
            if (isJerseyReturnValue) {
                if (result instanceof String)
                    // Jersey does not do json encoding if the return type is a string,
                    // expecting the returner to do the json encoding himself
                    // cf discussion at https://github.com/dropwizard/dropwizard/issues/231
                    result = JavaStringEscapes.wrapJavaString((String)result);
            }
            
            return result;
        } else {
            if (value==null) return "";
            return value.toString();            
        }
    }

}
