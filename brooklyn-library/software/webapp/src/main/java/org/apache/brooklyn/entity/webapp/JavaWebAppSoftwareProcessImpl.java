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
package org.apache.brooklyn.entity.webapp;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.entity.java.JavaAppUtils;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.collect.Sets;

public abstract class JavaWebAppSoftwareProcessImpl extends SoftwareProcessImpl implements JavaWebAppService, JavaWebAppSoftwareProcess {

    private static final Logger LOG = LoggerFactory.getLogger(JavaWebAppSoftwareProcessImpl.class);

    public JavaWebAppSoftwareProcessImpl(){
        super();
    }

    @SuppressWarnings("rawtypes")
    public JavaWebAppSoftwareProcessImpl(Entity parent){
        this(new LinkedHashMap(),parent);
    }

    @SuppressWarnings("rawtypes")
    public JavaWebAppSoftwareProcessImpl(Map flags){
        this(flags, null);
    }

    @SuppressWarnings("rawtypes")
    public JavaWebAppSoftwareProcessImpl(Map flags, Entity parent) {
        super(flags, parent);
    }

    @Override
    public void init() {
        super.init();
        
        WebAppServiceMethods.connectWebAppServerPolicies(this);
        JavaAppUtils.connectJavaAppServerPolicies(this);
    }
    
    //just provide better typing
    public JavaWebAppDriver getDriver() {
        return (JavaWebAppDriver) super.getDriver();
    }

    // TODO thread-safety issues: if multiple concurrent calls, may break (e.g. deployment_wars being reset)
    public void deployInitialWars() {
        if (getAttribute(DEPLOYED_WARS) == null) sensors().set(DEPLOYED_WARS, Sets.<String>newLinkedHashSet());
        
        String rootWar = getConfig(ROOT_WAR);
        if (rootWar!=null) deploy(rootWar, "ROOT.war");

        List<String> namedWars = getConfig(NAMED_WARS, Collections.<String>emptyList());
        for(String war: namedWars){
            deploy(war, getDriver().getFilenameContextMapper().findArchiveNameFromUrl(war, true));
        }
        
        Map<String,String> warsByContext = getConfig(WARS_BY_CONTEXT);
        if (warsByContext!=null) {
            for (String context: warsByContext.keySet()) {
                deploy(warsByContext.get(context), context);
            }
        }
    }

    /**
     * Deploys the given artifact, from a source URL, to a given deployment filename/context.
     * There is some variance in expected filename/context at various servers,
     * so the following conventions are followed:
     * <p>
     *   either ROOT.WAR or /       denotes root context
     * <p>
     *   anything of form  FOO.?AR  (ending .?AR) is copied with that name (unless copying not necessary)
     *                              and is expected to be served from /FOO
     * <p>
     *   anything of form  /FOO     (with leading slash) is expected to be served from /FOO
     *                              (and is copied as FOO.WAR)
     * <p>
     *   anything of form  FOO      (without a dot) is expected to be served from /FOO
     *                              (and is copied as FOO.WAR)
     * <p>                            
     *   otherwise <i>please note</i> behaviour may vary on different appservers;
     *   e.g. FOO.FOO would probably be ignored on appservers which expect a file copied across (usually),
     *   but served as /FOO.FOO on systems that take a deployment context.
     * <p>
     * See {@link FileNameToContextMappingTest} for definitive examples!
     * 
     * @param url  where to get the war, as a URL, either classpath://xxx or file:///home/xxx or http(s)...
     * @param targetName  where to tell the server to serve the WAR, see above
     */
    @Effector(description="Deploys the given artifact, from a source URL, to a given deployment filename/context")
    public void deploy(
            @EffectorParam(name="url", description="URL of WAR file") String url, 
            @EffectorParam(name="targetName", description="context path where WAR should be deployed (/ for ROOT)") String targetName) {
        try {
            checkNotNull(url, "url");
            checkNotNull(targetName, "targetName");
            JavaWebAppDriver driver = getDriver();
            String deployedName = driver.deploy(url, targetName);
            
            // Update attribute
            Set<String> deployedWars = getAttribute(DEPLOYED_WARS);
            if (deployedWars == null) {
                deployedWars = Sets.newLinkedHashSet();
            }
            deployedWars.add(deployedName);
            sensors().set(DEPLOYED_WARS, deployedWars);
        } catch (RuntimeException e) {
            // Log and propagate, so that log says which entity had problems...
            LOG.warn("Error deploying '"+url+"' to "+targetName+" on "+toString()+"; rethrowing...", e);
            throw Throwables.propagate(e);
        }
    }

    /** For the DEPLOYED_WARS to be updated, the input must match the result of the call to deploy */
    @Override
    @Effector(description="Undeploys the given context/artifact")
    public void undeploy(
            @EffectorParam(name="targetName") String targetName) {
        try {
            JavaWebAppDriver driver = getDriver();
            driver.undeploy(targetName);
            
            // Update attribute
            Set<String> deployedWars = getAttribute(DEPLOYED_WARS);
            if (deployedWars == null) {
                deployedWars = Sets.newLinkedHashSet();
            }
            deployedWars.remove( driver.getFilenameContextMapper().convertDeploymentTargetNameToContext(targetName) );
            sensors().set(DEPLOYED_WARS, deployedWars);
        } catch (RuntimeException e) {
            // Log and propagate, so that log says which entity had problems...
            LOG.warn("Error undeploying '"+targetName+"' on "+toString()+"; rethrowing...", e);
            throw Throwables.propagate(e);
        }
    }
    
    @Override
    protected void postStop() {
        super.postStop();
        // zero our workrate derived workrates.
        // TODO might not be enough, as policy may still be executing and have a record of historic vals; should remove policies
        // (also not sure we want this; implies more generally a responsibility for sensors to announce things when disconnected,
        // vs them just showing the last known value...)
        sensors().set(REQUESTS_PER_SECOND_LAST, 0D);
        sensors().set(REQUESTS_PER_SECOND_IN_WINDOW, 0D);
    }

    public boolean isHttpEnabled() {
        return WebAppServiceMethods.isProtocolEnabled(this, "HTTP");
    }

    public boolean isHttpsEnabled() {
        return WebAppServiceMethods.isProtocolEnabled(this, "HTTPS");
    }

    public Integer getHttpPort() {
        return getAttribute(HTTP_PORT);
    }

    public Integer getHttpsPort() {
        return getAttribute(HTTPS_PORT);
    }

    public String getHttpsSslKeyAlias() {
        HttpsSslConfig config = getAttribute(HTTPS_SSL_CONFIG);
        return (config == null) ? null : config.getKeyAlias();
    }

    public String getHttpsSslKeystorePassword() {
        HttpsSslConfig config = getAttribute(HTTPS_SSL_CONFIG);
        return (config == null) ? "" : config.getKeystorePassword();
    }

}
