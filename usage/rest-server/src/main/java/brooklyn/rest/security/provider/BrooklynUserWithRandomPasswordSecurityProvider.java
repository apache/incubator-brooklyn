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

import brooklyn.management.ManagementContext;
import brooklyn.rest.security.BrooklynPropertiesSecurityFilter;
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
        if (isRemoteAddressLocalhost(session) || (USER.equals(user) && this.password.equals(password))) {
            return allow(session, user);
        }
        return false;
    }

    private boolean isRemoteAddressLocalhost(HttpSession session) {
        Object remoteAddress = session.getAttribute(BrooklynPropertiesSecurityFilter.REMOTE_ADDRESS_SESSION_ATTRIBUTE);
        if (!(remoteAddress instanceof String)) return false;
        return Networking.isLocalhost((String)remoteAddress);
    }

}
