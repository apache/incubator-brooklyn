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

import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.StringConfigMap;

import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.rest.BrooklynWebConfig;

import brooklyn.util.text.Strings;

public class DelegatingSecurityProvider implements SecurityProvider {

    private static final Logger log = LoggerFactory.getLogger(DelegatingSecurityProvider.class);
    protected final ManagementContext mgmt;

    public DelegatingSecurityProvider(ManagementContext mgmt) {
        this.mgmt = mgmt;
        mgmt.addPropertiesReloadListener(new PropertiesListener());
    }
    
    private SecurityProvider delegate;
    private final AtomicLong modCount = new AtomicLong();

    private class PropertiesListener implements ManagementContext.PropertiesReloadListener {
        private static final long serialVersionUID = 8148722609022378917L;

        @Override
        public void reloaded() {
            log.debug("{} reloading security provider", DelegatingSecurityProvider.this);
            synchronized (DelegatingSecurityProvider.this) {
                loadDelegate();
                invalidateExistingSessions();
            }
        }
    }

    public synchronized SecurityProvider getDelegate() {
        if (delegate == null) {
            delegate = loadDelegate();
        }
        return delegate;
    }

    @SuppressWarnings("unchecked")
    private synchronized SecurityProvider loadDelegate() {
        StringConfigMap brooklynProperties = mgmt.getConfig();

        String className = brooklynProperties.getConfig(BrooklynWebConfig.SECURITY_PROVIDER_CLASSNAME);

        if (delegate != null && BrooklynWebConfig.hasNoSecurityOptions(mgmt.getConfig())) {
            log.debug("{} refusing to change from {}: No security provider set in reloaded properties.",
                    this, delegate);
            return delegate;
        }
        log.info("REST using security provider " + className);

        try {
            Class<? extends SecurityProvider> clazz;
            try {
                clazz = (Class<? extends SecurityProvider>) Class.forName(className);
            } catch (Exception e) {
                String oldPackage = "brooklyn.web.console.security.";
                if (className.startsWith(oldPackage)) {
                    className = Strings.removeFromStart(className, oldPackage);
                    className = DelegatingSecurityProvider.class.getPackage().getName() + "." + className;
                    clazz = (Class<? extends SecurityProvider>) Class.forName(className);
                    log.warn("Deprecated package " + oldPackage + " detected; please update security provider to point to " + className);
                } else throw e;
            }

            Constructor<? extends SecurityProvider> constructor;
            try {
                constructor = clazz.getConstructor(ManagementContext.class);
                delegate = constructor.newInstance(mgmt);
            } catch (Exception e) {
                constructor = clazz.getConstructor();
                Object delegateO = constructor.newInstance();
                if (!(delegateO instanceof SecurityProvider)) {
                    // if classloaders get mangled it will be a different CL's SecurityProvider
                    throw new ClassCastException("Delegate is either not a security provider or has an incompatible classloader: "+delegateO);
                }
                delegate = (SecurityProvider) delegateO;
            }
        } catch (Exception e) {
            log.warn("REST unable to instantiate security provider " + className + "; all logins are being disallowed", e);
            delegate = new BlackholeSecurityProvider();
        }
        return delegate;
    }

    /**
     * Causes all existing sessions to be invalidated.
     */
    protected void invalidateExistingSessions() {
        modCount.incrementAndGet();
    }

    @Override
    public boolean isAuthenticated(HttpSession session) {
        if (session == null) return false;
        Object modCountWhenFirstAuthenticated = session.getAttribute(getModificationCountKey());
        boolean authenticated = getDelegate().isAuthenticated(session) &&
                Long.valueOf(modCount.get()).equals(modCountWhenFirstAuthenticated);
        return authenticated;
    }

    @Override
    public boolean authenticate(HttpSession session, String user, String password) {
        boolean authenticated = getDelegate().authenticate(session, user, password);
        if (authenticated) {
            session.setAttribute(getModificationCountKey(), modCount.get());
        }
        if (log.isTraceEnabled() && authenticated) {
            log.trace("User {} authenticated with provider {}", user, getDelegate());
        } else if (!authenticated && log.isDebugEnabled()) {
            log.debug("Failed authentication for user {} with provider {}", user, getDelegate());
        }
        return authenticated;
    }

    @Override
    public boolean logout(HttpSession session) { 
        boolean logout = getDelegate().logout(session);
        if (logout) {
            session.removeAttribute(getModificationCountKey());
        }
        return logout;
    }

    private String getModificationCountKey() {
        return getClass().getName() + ".ModCount";
    }
}
