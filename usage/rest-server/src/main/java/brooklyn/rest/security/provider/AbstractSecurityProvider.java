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
package brooklyn.rest.security.provider;

import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides default implementations of {@link #isAuthenticated(HttpSession)} and
 * {@link #logout(HttpSession)}.
 */
public abstract class AbstractSecurityProvider implements SecurityProvider {

    public static final Logger LOG = LoggerFactory.getLogger(AbstractSecurityProvider.class);

    @Override
    public boolean isAuthenticated(HttpSession session) {
        if (session == null) return false;
        Object value = session.getAttribute(getAuthenticationKey());
        return value != null && Boolean.TRUE.equals(value);
    }

    @Override
    public boolean logout(HttpSession session) {
        if (session == null) return false;
        session.removeAttribute(getAuthenticationKey());
        return true;
    }

    /**
     * Sets an authentication token for the user on the session. Always returns true.
     */
    protected boolean allow(HttpSession session, String user) {
        LOG.debug("Web console {} authenticated user {}", getClass().getSimpleName(), user);
        session.setAttribute(getAuthenticationKey(), user);
        return true;
    }

    protected String getAuthenticationKey() {
        return getClass().getName() + ".AUTHENTICATED";
    }

}
