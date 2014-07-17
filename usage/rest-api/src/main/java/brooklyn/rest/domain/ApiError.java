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
package brooklyn.rest.domain;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import brooklyn.util.text.Strings;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;

public class ApiError {

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

        public Builder message(String message) {
            this.message = checkNotNull(message, "message");
            return this;
        }

        public Builder details(String details) {
            this.details = checkNotNull(details, "details");
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
            return new ApiError(message, details);
        }

        /** @deprecated since 0.7.0; use {@link #copy(ApiError)} */
        @Deprecated
        public Builder fromApiError(ApiError error) {
            return copy(error);
        }
        
        public Builder copy(ApiError error) {
            return this
                    .message(error.message)
                    .details(error.details);
        }
        
        public String getMessage() {
            return message;
        }
    }

    private final String message;
    
    @JsonSerialize(include=Inclusion.NON_EMPTY)
    private final String details;

    public ApiError(
            @JsonProperty("message") String message,
            @JsonProperty("details") String details) {
        this.message = checkNotNull(message, "message");
        this.details = details != null ? details : "";
    }

    public String getMessage() {
        return message;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        ApiError that = ApiError.class.cast(other);
        return Objects.equal(this.message, that.message) &&
                Objects.equal(this.details, that.details);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(message, details);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("message", message)
                .add("details", details)
                .toString();
    }

    public Response asBadRequestResponseJson() {
        return asResponse(Status.BAD_REQUEST, MediaType.APPLICATION_JSON_TYPE);
    }

    public Response asResponse(Status status, MediaType type) {
        return Response.status(status)
            .type(type)
            .entity(this)
            .build();
    }
}
