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
package org.apache.brooklyn.rest.security.provider;

import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.rest.filter.BrooklynPropertiesSecurityFilter;

import brooklyn.util.net.Networking;
import brooklyn.util.text.Identifiers;

public class BrooklynUserWithRandomPasswordSecurityProvider extends AbstractSecurityProvider implements SecurityProvider {

    public static final Logger LOG = LoggerFactory.getLogger(BrooklynUserWithRandomPasswordSecurityProvider.class);
    private static final String USER = "brooklyn";
    private final String password;

    public BrooklynUserWithRandomPasswordSecurityProvider() {
        this.password = Identifiers.makeRandomId(10);
        LOG.info("Allowing access to web console from localhost or with {}:{}", USER, password);
    }

    public BrooklynUserWithRandomPasswordSecurityProvider(ManagementContext mgmt) {
        this();
    }

    @Override
    public boolean authenticate(HttpSession session, String user, String password) {
        if ((USER.equals(user) && this.password.equals(password)) || isRemoteAddressLocalhost(session)) {
            return allow(session, user);
        } else {
            return false;
        }
    }

    private boolean isRemoteAddressLocalhost(HttpSession session) {
        Object remoteAddress = session.getAttribute(BrooklynPropertiesSecurityFilter.REMOTE_ADDRESS_SESSION_ATTRIBUTE);
        if (!(remoteAddress instanceof String)) return false;
        if (Networking.isLocalhost((String)remoteAddress)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace(this+": granting passwordless access to "+session+" originating from "+remoteAddress);
            }
            return true;
        } else {
            LOG.debug(this+": password required for "+session+" originating from "+remoteAddress);
            return false;
        }
    }
}
