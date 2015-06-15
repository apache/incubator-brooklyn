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
package brooklyn.util.http;

import java.lang.annotation.Annotation;

import javax.ws.rs.core.Response;

import org.jboss.resteasy.core.Headers;
import org.jboss.resteasy.specimpl.BuiltResponse;

import brooklyn.util.exceptions.Exceptions;

/** 
 * Allows wrapping a {@link Response} with the stream fully read and closed so that the client can be re-used.
 * <p>
 * The entity may be stored as a string as type info is not available when it is deserialized, 
 * and that's a relatively convenient common format.
 *  
 * TODO It would be nice to support other parsing, storing the byte array.
 */
public class BuiltResponsePreservingError extends BuiltResponse {

    private Throwable error;

    public BuiltResponsePreservingError(int status, Headers<Object> headers, Object entity, Annotation[] annotations, Throwable error) {
        super(status, headers, entity, annotations);
        this.error = error;
    }
    
    @SuppressWarnings("deprecation")
    public static <T> Response copyResponseAndClose(Response source, Class<T> type) {
        int status = -1;
        Headers<Object> headers = new Headers<Object>();
        Object entity = null;
        try {
            status = source.getStatus();
            headers.putAll(source.getHeaders());
            if (source instanceof org.jboss.resteasy.client.ClientResponse) {
                // ClientResponse requires strong type info, which we don't yet have
                entity = ((org.jboss.resteasy.client.ClientResponse<?>)source).getEntity(type);
            } else {
                entity = source.getEntity();
            }
            return new BuiltResponsePreservingError(status, headers, entity, new Annotation[0], null);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            return new BuiltResponsePreservingError(status, headers, entity, new Annotation[0], e);
        } finally {
            source.close();
        }
    }
    
    @Override
    public Object getEntity() {
        if (error!=null) Exceptions.propagate(error);
        return super.getEntity();
    }

}
