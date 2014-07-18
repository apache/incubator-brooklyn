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
package brooklyn.entity.webapp;

import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.java.UsesJava;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

public interface JavaWebAppService extends WebAppService, UsesJava {

	@SetFromFlag("war")
	public static final ConfigKey<String> ROOT_WAR = new BasicConfigKey<String>(
	        String.class, "wars.root", "WAR file to deploy as the ROOT, as URL (supporting file: and classpath: prefixes)");

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SetFromFlag("wars")
	public static final ConfigKey<List<String>> NAMED_WARS = new BasicConfigKey(
	        List.class, "wars.named", "Archive files to deploy, as URL strings (supporting file: and classpath: prefixes); context (path in user-facing URL) will be inferred by name");
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SetFromFlag("warsByContext")
    public static final ConfigKey<Map<String,String>> WARS_BY_CONTEXT = new BasicConfigKey(
            Map.class, "wars.by.context", "Map of context keys (path in user-facing URL, typically without slashes) to archives (e.g. WARs by URL) to deploy, supporting file: and classpath: prefixes)");
    
    /** Optional marker interface for entities which support 'deploy' and 'undeploy' */
    public interface CanDeployAndUndeploy extends Entity {

        @SuppressWarnings({ "unchecked", "rawtypes" })
        public static final AttributeSensor<Set<String>> DEPLOYED_WARS = new BasicAttributeSensor(
                Set.class, "webapp.deployedWars", "Names of archives/contexts that are currently deployed");

        public static final MethodEffector<Void> DEPLOY = new MethodEffector<Void>(CanDeployAndUndeploy.class, "deploy");
        public static final MethodEffector<Void> UNDEPLOY = new MethodEffector<Void>(CanDeployAndUndeploy.class, "undeploy");

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
                @EffectorParam(name="targetName", description="context path where WAR should be deployed (/ for ROOT)") String targetName);

        /** 
         * For the DEPLOYED_WARS to be updated, the input must match the result of the call to deploy,
         * e.g. the transformed name using 
         */
        @Effector(description="Undeploys the given context/artifact")
        public void undeploy(
                @EffectorParam(name="targetName") String targetName);
    }

    /** Optional marker interface for entities which support 'redeployAll' */
    public interface CanRedeployAll {
        public static final MethodEffector<Void> REDEPLOY_ALL = new MethodEffector<Void>(CanRedeployAll.class, "redeployAll");
        
        @Effector(description="Redeploys all web apps known here across the cluster (e.g. if it gets into an inconsistent state)")
        public void redeployAll();
    }
        
}
