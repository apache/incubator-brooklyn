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
package brooklyn.rest.filter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Set;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;

import brooklyn.config.BrooklynLogging;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;

/**
 * Handles logging of request information.
 */
public class LoggingFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynLogging.REST);

    /** Methods logged at debug rather than trace. */
    private static final Set<String> INTERESTING_METHODS = Sets.newHashSet("POST", "PUT", "DELETE");

    /** Headers whose values will not be logged. */
    private static final Set<String> CENSORED_HEADERS = Sets.newHashSet("Authorization");

    @Override
    public void init(FilterConfig config) throws ServletException {
    }

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String uri = httpRequest.getRequestURI();
        String rid = RequestTaggingFilter.getTag();
        boolean isInteresting = INTERESTING_METHODS.contains(httpRequest.getMethod()),
                shouldLog = (isInteresting && LOG.isDebugEnabled()) || LOG.isTraceEnabled(),
                requestErrored = false;
        Stopwatch timer = Stopwatch.createUnstarted();
        try {
            if (shouldLog) {
                String message = "{} starting request {} {}";
                Object[] args = new Object[]{rid, httpRequest.getMethod(), uri};
                if (isInteresting) {
                    LOG.debug(message, args);
                } else {
                    LOG.trace(message, args);
                }
            }

            timer.start();
            chain.doFilter(request, response);

        } catch (Throwable e) {
            requestErrored = true;
            LOG.warn("REST API request " + rid + " failed: " + e, e);
            // Propagate for handling by other filter
            throw Exceptions.propagate(e);
        } finally {
            timer.stop();
            // This logging must not happen before chain.doFilter, or FormMapProvider will not work as expected.
            // Getting the parameter map consumes the request body and only resource methods using @FormParam
            // will work as expected.
            if (requestErrored || shouldLog) {
                boolean includeHeaders = requestErrored || httpResponse.getStatus() / 100 == 5 || LOG.isTraceEnabled();
                String message = getRequestCompletedMessage(includeHeaders, Duration.of(timer), rid, httpRequest, httpResponse);
                if (requestErrored || isInteresting) {
                    LOG.debug(message);
                } else {
                    LOG.trace(message);
                }
            }
        }
    }

    private String getRequestCompletedMessage(boolean includeHeaders, Duration elapsed,
            String id, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        StringBuilder message = new StringBuilder(id)
                .append(" complete in roughly ")
                .append(elapsed)
                .append(". Responding ")
                .append(httpResponse.getStatus())
                .append(" for ")
                .append(httpRequest.getMethod())
                .append(" ")
                .append(httpRequest.getRequestURI())
                .append(" from ")
                .append(httpRequest.getRemoteAddr());

        if (!httpRequest.getParameterMap().isEmpty()) {
            message.append(", parameters: ")
                    .append(Joiner.on(", ").withKeyValueSeparator("=").join(httpRequest.getParameterMap()));
        }
        if (httpRequest.getContentLength() > 0) {
            int len = httpRequest.getContentLength();
            message.append(" contentType=").append(httpRequest.getContentType())
                    .append(" (length=").append(len).append(")");
        }
        if (includeHeaders) {
            Enumeration<String> headerNames = httpRequest.getHeaderNames();
            if (headerNames.hasMoreElements()) {
                message.append(", headers: ");
                while (headerNames.hasMoreElements()) {
                    String headerName = headerNames.nextElement();
                    message.append(headerName).append(": ");
                    if (CENSORED_HEADERS.contains(headerName)) {
                        message.append("******");
                    } else {
                        message.append(httpRequest.getHeader(headerName));
                        if (headerNames.hasMoreElements()) {
                            message.append(", ");
                        }
                    }
                }
            }
        }

        return message.toString();
    }

}
