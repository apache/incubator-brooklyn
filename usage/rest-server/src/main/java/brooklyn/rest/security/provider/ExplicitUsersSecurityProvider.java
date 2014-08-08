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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringTokenizer;

import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.StringConfigMap;
import brooklyn.management.ManagementContext;
import brooklyn.rest.BrooklynWebConfig;
import brooklyn.rest.security.PasswordHasher;

/** security provider which validates users against passwords according to property keys,
 * as set in {@link BrooklynWebConfig#USERS} and {@link BrooklynWebConfig#PASSWORD_FOR_USER(String)}*/
public class ExplicitUsersSecurityProvider implements SecurityProvider {

    public static final Logger LOG = LoggerFactory.getLogger(ExplicitUsersSecurityProvider.class);
    
    public static final String AUTHENTICATION_KEY = ExplicitUsersSecurityProvider.class.getCanonicalName()+"."+"AUTHENTICATED";

    protected final ManagementContext mgmt;
    
    public ExplicitUsersSecurityProvider(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }
    
    @Override
    public boolean isAuthenticated(HttpSession session) {
        if (session==null) return false;
        Object value = session.getAttribute(AUTHENTICATION_KEY);
        return (value!=null);
    }

    private boolean allowAnyUserWithValidPass = false;
    
    private Set<String> allowedUsers = null;
    
    private synchronized void initialize() {
        if (allowedUsers!=null) return;

        StringConfigMap properties = mgmt.getConfig();

        allowedUsers = new LinkedHashSet<String>();
        String users = properties.getConfig(BrooklynWebConfig.USERS);
        if (users==null) {
            LOG.warn("Web console has no users configured; no one will be able to log in!");
        } else if ("*".equals(users)) {
            LOG.info("Web console allowing any user (so long as valid password is set)");
            allowAnyUserWithValidPass = true;
        } else {
            StringTokenizer t = new StringTokenizer(users, ",");
            while (t.hasMoreElements()) {
                allowedUsers.add((""+t.nextElement()).trim());
            }
            LOG.info("Web console allowing users: "+allowedUsers);
        }       
    }
    
    @Override
    public boolean authenticate(HttpSession session, String user, String password) {
        if (session==null || user==null) return false;
        
        initialize();
        
        if (!allowAnyUserWithValidPass) {
            if (!allowedUsers.contains(user)) {
                LOG.info("Web console rejecting unknown user "+user);
                return false;                
            }
        }

        BrooklynProperties properties = (BrooklynProperties) mgmt.getConfig();
        String expectedP = properties.getConfig(BrooklynWebConfig.PASSWORD_FOR_USER(user));
        String salt = properties.getConfig(BrooklynWebConfig.SALT_FOR_USER(user));
        String expectedSha256 = properties.getConfig(BrooklynWebConfig.SHA256_FOR_USER(user));
        
        if (expectedP != null) {
            if (expectedP.equals(password)){
                // password is good
                return allow(session, user);
            } else {
                LOG.info("Web console rejecting bad password for user "+user);
                return false;
            }
        }
        if (expectedSha256 != null) {
            String hashedPassword = PasswordHasher.sha256(salt, password);
            if (expectedSha256.equals(hashedPassword)) {
                // hashed password is good
                return allow(session, user);
            } else {
                LOG.info("Web console rejecting bad password for user "+user);
                return false;
            }                
        }
        LOG.warn("Web console rejecting passwordless user "+user);
        return false;
    }

    private boolean allow(HttpSession session, String user) {
        LOG.debug("Web console "+getClass().getSimpleName()+" authenticated user "+user);
        session.setAttribute(AUTHENTICATION_KEY, user);
        return true;
    }

    @Override
    public boolean logout(HttpSession session) { 
        if (session==null) return false;
        session.removeAttribute(AUTHENTICATION_KEY);
        return true;
    }

}
