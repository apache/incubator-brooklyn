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
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.StringConfigMap;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
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
        initialize();
    }

    private synchronized void initialize() {
        if (allowedUsers != null) return;

        StringConfigMap properties = mgmt.getConfig();

        allowedUsers = new LinkedHashSet<String>();
        String users = properties.getConfig(BrooklynWebConfig.USERS);
        if (users == null) {
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
        
        if (!allowAnyUserWithValidPass) {
            if (!allowedUsers.contains(user)) {
                LOG.debug("REST rejecting unknown user "+user);
                return false;                
            }
        }

        if (checkExplicitUserPassword(mgmt, user, password)) {
            return allow(session, user);
        }
        return false;
    }

    /** checks the supplied candidate user and password against the
     * expect password (or SHA-256 + SALT thereof) defined as brooklyn properties.
     */
    public static boolean checkExplicitUserPassword(ManagementContext mgmt, String user, String password) {
        BrooklynProperties properties = ((ManagementContextInternal)mgmt).getBrooklynProperties();
        String expectedPassword = properties.getConfig(BrooklynWebConfig.PASSWORD_FOR_USER(user));
        String salt = properties.getConfig(BrooklynWebConfig.SALT_FOR_USER(user));
        String expectedSha256 = properties.getConfig(BrooklynWebConfig.SHA256_FOR_USER(user));
        
        return checkPassword(password, expectedPassword, expectedSha256, salt);
    }
    /** 
     * checks a candidate password against the expected credential defined for a given user.
     * the expected credentials can be supplied as an expectedPassword OR as
     * a combination of the SHA-256 hash of the expected password plus a defined salt.
     * the combination of the SHA+SALT allows credentials to be supplied in a non-plaintext manner.
     */
    public static boolean checkPassword(String candidatePassword, String expectedPassword, String expectedPasswordSha256, String salt) {
        if (expectedPassword != null) {
            return expectedPassword.equals(candidatePassword);
        } else if (expectedPasswordSha256 != null) {
            String hashedCandidatePassword = PasswordHasher.sha256(salt, candidatePassword);
            return expectedPasswordSha256.equals(hashedCandidatePassword);
        }

        return false;
    }
}
