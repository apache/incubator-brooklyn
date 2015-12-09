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
package org.apache.brooklyn.rest.domain;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.brooklyn.util.text.Strings;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;

public class ApiError implements Serializable {

    private static final long serialVersionUID = -8244515572813244686L;

    public static Builder builder() {
        return new Builder();
    }

    public static ApiError of(Throwable t) {
        return builderFromThrowable(t).build();
    }
    
    public static ApiError of(String message) {
        return builder().message(message).build();
    }
    
    /** @deprecated since 0.7.0; use {@link #builderFromThrowable(Throwable)} */
    @Deprecated
    public static Builder fromThrowable(Throwable t) {
        return builderFromThrowable(t);
    }
    
    /**
     * @return An {@link ApiError.Builder} whose message is initialised to either the throwable's
     *         message or the throwable's class name if the message is null and whose details are
     *         initialised to the throwable's stack trace.
     */
    public static Builder builderFromThrowable(Throwable t) {
        checkNotNull(t, "throwable");
        String message = Optional.fromNullable(t.getMessage())
                .or(t.getClass().getName());
        return builder()
                .message(message)
                .details(Throwables.getStackTraceAsString(t));
    }

    public static class Builder {
        private String message;
        private String details;
        private Integer errorCode;

        public Builder message(String message) {
            this.message = checkNotNull(message, "message");
            return this;
        }

        public Builder details(String details) {
            this.details = checkNotNull(details, "details");
            return this;
        }

        public Builder errorCode(Status errorCode) {
            return errorCode(errorCode.getStatusCode());
        }
        
        public Builder errorCode(Integer errorCode) {
            this.errorCode = errorCode;
            return this;
        }
        
        /** as {@link #prefixMessage(String, String)} with default separator of `: ` */
        public Builder prefixMessage(String prefix) {
            return prefixMessage(prefix, ": ");
        }
        
        /** puts a prefix in front of the message, with the given separator if there is already a message;
         * if there is no message, it simply sets the prefix as the message
         */
        public Builder prefixMessage(String prefix, String separatorIfMessageNotBlank) {
            if (Strings.isBlank(message)) message(prefix);
            else message(prefix+separatorIfMessageNotBlank+message);
            return this;
        }
        
        public ApiError build() {
            return new ApiError(message, details, errorCode);
        }

        /** @deprecated since 0.7.0; use {@link #copy(ApiError)} */
        @Deprecated
        public Builder fromApiError(ApiError error) {
            return copy(error);
        }
        
        public Builder copy(ApiError error) {
            return this
                    .message(error.message)
                    .details(error.details)
                    .errorCode(error.error);
        }
        
        public String getMessage() {
            return message;
        }
    }

    private final String message;
    
    @JsonSerialize(include=Inclusion.NON_EMPTY)
    private final String details;

    @JsonSerialize(include=Inclusion.NON_NULL)
    private final Integer error;

    public ApiError(String message) { this(message, null); }
    public ApiError(String message, String details) { this(message, details, null); }
    public ApiError(
            @JsonProperty("message") String message,
            @JsonProperty("details") String details,
            @JsonProperty("error") Integer error) {
        this.message = checkNotNull(message, "message");
        this.details = details != null ? details : "";
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public String getDetails() {
        return details;
    }

    public Integer getError() {
        return error;
    }
    
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        ApiError that = ApiError.class.cast(other);
        return Objects.equal(this.message, that.message) &&
                Objects.equal(this.details, that.details) &&
                Objects.equal(this.error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(message, details, error);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("message", message)
                .add("details", details)
                .add("error", error)
                .toString();
    }

    public Response asBadRequestResponseJson() {
        return asResponse(Status.BAD_REQUEST, MediaType.APPLICATION_JSON_TYPE);
    }

    public Response asResponse(Status defaultStatus, MediaType type) {
        return Response.status(error!=null ? error : defaultStatus!=null ? defaultStatus.getStatusCode() : Status.INTERNAL_SERVER_ERROR.getStatusCode())
            .type(type)
            .entity(this)
            .build();
    }
    
    public Response asResponse(MediaType type) {
        return asResponse(null, type);
    }
    
    public Response asJsonResponse() {
        return asResponse(MediaType.APPLICATION_JSON_TYPE);
    }
}
