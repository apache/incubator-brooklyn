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
package org.apache.brooklyn.location.jclouds;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.brooklyn.util.JavaGroovyEquivalents.groovyTruth;
import static org.jclouds.aws.ec2.reference.AWSEC2Constants.PROPERTY_EC2_AMI_QUERY;
import static org.jclouds.aws.ec2.reference.AWSEC2Constants.PROPERTY_EC2_CC_AMI_QUERY;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.brooklyn.core.config.Sanitizer;
import org.apache.brooklyn.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.time.Duration;
import org.jclouds.Constants;
import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.ec2.reference.EC2Constants;
import org.jclouds.encryption.bouncycastle.config.BouncyCastleCryptoModule;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.inject.Module;

public class ComputeServiceRegistryImpl implements ComputeServiceRegistry, JcloudsLocationConfig {
    
    private static final Logger LOG = LoggerFactory.getLogger(ComputeServiceRegistryImpl.class);

    public static final ComputeServiceRegistryImpl INSTANCE = new ComputeServiceRegistryImpl();
        
    protected ComputeServiceRegistryImpl() {
    }
    
    protected final Map<Map<?,?>,ComputeService> cachedComputeServices = new ConcurrentHashMap<Map<?,?>,ComputeService>();

    protected final Object createComputeServicesMutex = new Object();

    @Override
    public ComputeService findComputeService(ConfigBag conf, boolean allowReuse) {
        String provider = checkNotNull(conf.get(CLOUD_PROVIDER), "provider must not be null");
        String identity = checkNotNull(conf.get(CloudLocationConfig.ACCESS_IDENTITY), "identity must not be null");
        String credential = checkNotNull(conf.get(CloudLocationConfig.ACCESS_CREDENTIAL), "credential must not be null");
        
        Properties properties = new Properties();
        properties.setProperty(Constants.PROPERTY_TRUST_ALL_CERTS, Boolean.toString(true));
        properties.setProperty(Constants.PROPERTY_RELAX_HOSTNAME, Boolean.toString(true));
        properties.setProperty("jclouds.ssh.max-retries", conf.getStringKey("jclouds.ssh.max-retries") != null ? 
                conf.getStringKey("jclouds.ssh.max-retries").toString() : "50");
        // Enable aws-ec2 lazy image fetching, if given a specific imageId; otherwise customize for specific owners; or all as a last resort
        // See https://issues.apache.org/jira/browse/WHIRR-416
        if ("aws-ec2".equals(provider)) {
            // TODO convert AWS-only flags to config keys
            if (groovyTruth(conf.get(IMAGE_ID))) {
                properties.setProperty(PROPERTY_EC2_AMI_QUERY, "");
                properties.setProperty(PROPERTY_EC2_CC_AMI_QUERY, "");
            } else if (groovyTruth(conf.getStringKey("imageOwner"))) {
                properties.setProperty(PROPERTY_EC2_AMI_QUERY, "owner-id="+conf.getStringKey("imageOwner")+";state=available;image-type=machine");
            } else if (groovyTruth(conf.getStringKey("anyOwner"))) {
                // set `anyOwner: true` to override the default query (which is restricted to certain owners as per below), 
                // allowing the AMI query to bind to any machine
                // (note however, we sometimes pick defaults in JcloudsLocationFactory);
                // (and be careful, this can give a LOT of data back, taking several minutes,
                // and requiring extra memory allocated on the command-line)
                properties.setProperty(PROPERTY_EC2_AMI_QUERY, "state=available;image-type=machine");
                /*
                 * by default the following filters are applied:
                 * Filter.1.Name=owner-id&Filter.1.Value.1=137112412989&
                 * Filter.1.Value.2=063491364108&
                 * Filter.1.Value.3=099720109477&
                 * Filter.1.Value.4=411009282317&
                 * Filter.2.Name=state&Filter.2.Value.1=available&
                 * Filter.3.Name=image-type&Filter.3.Value.1=machine&
                 */
            }
            
            // occasionally can get com.google.common.util.concurrent.UncheckedExecutionException: java.lang.RuntimeException: 
            //     security group eu-central-1/jclouds#brooklyn-bxza-alex-eu-central-shoul-u2jy-nginx-ielm is not available after creating
            // the default timeout was 500ms so let's raise it in case that helps
            properties.setProperty(EC2Constants.PROPERTY_EC2_TIMEOUT_SECURITYGROUP_PRESENT, ""+Duration.seconds(30).toMilliseconds());
        }

        // FIXME Deprecated mechanism, should have a ConfigKey for overrides
        Map<String, Object> extra = Maps.filterKeys(conf.getAllConfig(), Predicates.containsPattern("^jclouds\\."));
        if (extra.size() > 0) {
            LOG.warn("Jclouds using deprecated property overrides: "+Sanitizer.sanitize(extra));
        }
        properties.putAll(extra);

        String endpoint = conf.get(CloudLocationConfig.CLOUD_ENDPOINT);
        if (!groovyTruth(endpoint)) endpoint = getDeprecatedProperty(conf, Constants.PROPERTY_ENDPOINT);
        if (groovyTruth(endpoint)) properties.setProperty(Constants.PROPERTY_ENDPOINT, endpoint);

        Map<?,?> cacheKey = MutableMap.builder()
                .putAll(properties)
                .put("provider", provider)
                .put("identity", identity)
                .put("credential", credential)
                .putIfNotNull("endpoint", endpoint)
                .build()
                .asUnmodifiable();

        if (allowReuse) {
            ComputeService result = cachedComputeServices.get(cacheKey);
            if (result!=null) {
                LOG.trace("jclouds ComputeService cache hit for compute service, for "+Sanitizer.sanitize(properties));
                return result;
            }
            LOG.debug("jclouds ComputeService cache miss for compute service, creating, for "+Sanitizer.sanitize(properties));
        }

        Iterable<Module> modules = getCommonModules();

        // Synchronizing to avoid deadlock from sun.reflect.annotation.AnnotationType.
        // See https://github.com/brooklyncentral/brooklyn/issues/974
        ComputeServiceContext computeServiceContext;
        synchronized (createComputeServicesMutex) {
            computeServiceContext = ContextBuilder.newBuilder(provider)
                    .modules(modules)
                    .credentials(identity, credential)
                    .overrides(properties)
                    .build(ComputeServiceContext.class);
        }
        final ComputeService computeService = computeServiceContext.getComputeService();
        if (allowReuse) {
            synchronized (cachedComputeServices) {
                ComputeService result = cachedComputeServices.get(cacheKey);
                if (result != null) {
                    LOG.debug("jclouds ComputeService cache recovery for compute service, for "+Sanitizer.sanitize(cacheKey));
                    //keep the old one, discard the new one
                    computeService.getContext().close();
                    return result;
                }
                LOG.debug("jclouds ComputeService created "+computeService+", adding to cache, for "+Sanitizer.sanitize(properties));
                cachedComputeServices.put(cacheKey, computeService);
            }
        }
        return computeService;
     }

    /** returns the jclouds modules we typically install */ 
    protected ImmutableSet<Module> getCommonModules() {
        return ImmutableSet.<Module> of(
                new SshjSshClientModule(), 
                new SLF4JLoggingModule(),
                new BouncyCastleCryptoModule());
    }
     
    protected String getDeprecatedProperty(ConfigBag conf, String key) {
        if (conf.containsKey(key)) {
            LOG.warn("Jclouds using deprecated brooklyn-jclouds property "+key+": "+Sanitizer.sanitize(conf.getAllConfig()));
            return (String) conf.getStringKey(key);
        } else {
            return null;
        }
    }
}
