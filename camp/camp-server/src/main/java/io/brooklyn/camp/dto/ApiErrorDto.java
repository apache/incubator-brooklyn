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
package io.brooklyn.camp.dto;

import static com.google.common.base.Preconditions.checkNotNull;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;

/**
 * A simple error message that provides a message and optional details.
 *
 * This class should eventually be replaced with an ErrorMessage object,
 * as described in the CAMP spec.
 */
public class ApiErrorDto {

    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return An {@link ApiErrorDto.Builder} whose message is initialised to either the throwable's
     *         message or the throwable's class name if the message is null and whose details are
     *         initialised to the throwable's stack trace.
     */
    public static Builder fromThrowable(Throwable t) {
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

        public ApiErrorDto build() {
            return new ApiErrorDto(message, details);
        }

        public Builder fromApiErrorDto(ApiErrorDto error) {
            return this
                    .message(error.message)
                    .details(error.details);
        }
    }

    private final String message;
    private final String details;

    public ApiErrorDto(
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
        ApiErrorDto that = ApiErrorDto.class.cast(other);
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
}
