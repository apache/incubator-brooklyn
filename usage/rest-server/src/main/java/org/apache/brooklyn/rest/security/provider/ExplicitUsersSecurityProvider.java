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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringTokenizer;

import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.StringConfigMap;

import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.rest.BrooklynWebConfig;
import org.apache.brooklyn.rest.security.PasswordHasher;

/**
 * Security provider which validates users against passwords according to property keys,
 * as set in {@link BrooklynWebConfig#USERS} and {@link BrooklynWebConfig#PASSWORD_FOR_USER(String)}
 */
public class ExplicitUsersSecurityProvider extends AbstractSecurityProvider implements SecurityProvider {

    public static final Logger LOG = LoggerFactory.getLogger(ExplicitUsersSecurityProvider.class);
    
    protected final ManagementContext mgmt;
    private boolean allowAnyUserWithValidPass;
    private Set<String> allowedUsers = null;

    public ExplicitUsersSecurityProvider(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }

    private synchronized void initialize() {
        if (allowedUsers != null) return;

        StringConfigMap properties = mgmt.getConfig();

        allowedUsers = new LinkedHashSet<String>();
        String users = properties.getConfig(BrooklynWebConfig.USERS);
        if (users == null) {
            // TODO unfortunately this is only activated *when* someone tries to log in
            // (NB it seems like this class is not even instantiated until first log in)
            LOG.warn("REST has no users configured; no one will be able to log in!");
        } else if ("*".equals(users)) {
            LOG.info("REST allowing any user (so long as valid password is set)");
            allowAnyUserWithValidPass = true;
        } else {
            StringTokenizer t = new StringTokenizer(users, ",");
            while (t.hasMoreElements()) {
                allowedUsers.add(("" + t.nextElement()).trim());
            }
            LOG.info("REST allowing users: " + allowedUsers);
        }
    }

    
    @Override
    public boolean authenticate(HttpSession session, String user, String password) {
        if (session==null || user==null) return false;
        
        initialize();
        
        if (!allowAnyUserWithValidPass) {
            if (!allowedUsers.contains(user)) {
                LOG.debug("REST rejecting unknown user "+user);
                return false;                
            }
        }

        BrooklynProperties properties = (BrooklynProperties) mgmt.getConfig();
        String expectedP = properties.getConfig(BrooklynWebConfig.PASSWORD_FOR_USER(user));
        String salt = properties.getConfig(BrooklynWebConfig.SALT_FOR_USER(user));
        String expectedSha256 = properties.getConfig(BrooklynWebConfig.SHA256_FOR_USER(user));
        
        if (expectedP != null) {
            return expectedP.equals(password) && allow(session, user);
        } else if (expectedSha256 != null) {
            String hashedPassword = PasswordHasher.sha256(salt, password);
            return expectedSha256.equals(hashedPassword) && allow(session, user);
        }

        return false;
    }
}
