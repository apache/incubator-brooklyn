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

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CharMatcher;

import brooklyn.config.StringConfigMap;

import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.rest.BrooklynWebConfig;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.List;

/**
 * A {@link SecurityProvider} implementation that relies on LDAP to authenticate.
 *
 * @author Peter Veentjer.
 */
public class LdapSecurityProvider extends AbstractSecurityProvider implements SecurityProvider {

    public static final Logger LOG = LoggerFactory.getLogger(LdapSecurityProvider.class);

    public static final String LDAP_CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";

    private final String ldapUrl;
    private final String ldapRealm;
    private final String organizationUnit;

    public LdapSecurityProvider(ManagementContext mgmt) {
        StringConfigMap properties = mgmt.getConfig();
        ldapUrl = properties.getConfig(BrooklynWebConfig.LDAP_URL);
        Strings.checkNonEmpty(ldapUrl, "LDAP security provider configuration missing required property "+BrooklynWebConfig.LDAP_URL);
        ldapRealm = CharMatcher.isNot('"').retainFrom(properties.getConfig(BrooklynWebConfig.LDAP_REALM));
        Strings.checkNonEmpty(ldapRealm, "LDAP security provider configuration missing required property "+BrooklynWebConfig.LDAP_REALM);

        if(Strings.isBlank(properties.getConfig(BrooklynWebConfig.LDAP_OU))) {
            LOG.info("Setting LDAP ou attribute to: Users");
            organizationUnit = "Users";
        } else {
            organizationUnit = CharMatcher.isNot('"').retainFrom(properties.getConfig(BrooklynWebConfig.LDAP_OU));
        }
        Strings.checkNonEmpty(ldapRealm, "LDAP security provider configuration missing required property "+BrooklynWebConfig.LDAP_OU);
    }

    public LdapSecurityProvider(String ldapUrl, String ldapRealm, String organizationUnit) {
        this.ldapUrl = ldapUrl;
        this.ldapRealm = ldapRealm;
        this.organizationUnit = organizationUnit;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public boolean authenticate(HttpSession session, String user, String password) {
        if (session==null || user==null) return false;
        checkCanLoad();

        Hashtable env = new Hashtable();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapUrl);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, getUserDN(user));
        env.put(Context.SECURITY_CREDENTIALS, password);

        try {
            new InitialDirContext(env);
            return allow(session, user);
        } catch (NamingException e) {
            return false;
        }
    }

    /**
     * Returns the LDAP path for the user
     *
     * @param user
     * @return String
     */
    protected String getUserDN(String user) {
        List<String> domain = Lists.transform(Arrays.asList(ldapRealm.split("\\.")), new Function<String, String>() {
            @Override
            public String apply(String input) {
                return "dc=" + input;
            }
        });

        String dc = Joiner.on(",").join(domain).toLowerCase();
        return "cn=" + user + ",ou=" + organizationUnit + "," + dc;
    }

    static boolean triedLoading = false;
    public synchronized static void checkCanLoad() {
        if (triedLoading) return;
        try {
            Class.forName(LDAP_CONTEXT_FACTORY);
            triedLoading = true;
        } catch (Throwable e) {
            throw Exceptions.propagate(new ClassNotFoundException("Unable to load LDAP classes ("+LDAP_CONTEXT_FACTORY+") required for Brooklyn LDAP security provider"));
        }
    }
}
