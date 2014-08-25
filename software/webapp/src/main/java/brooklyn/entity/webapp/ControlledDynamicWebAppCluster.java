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

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.ConfigurableEntityFactory;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxy.LoadBalancer;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.MemberReplaceable;
import brooklyn.entity.trait.Resizable;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * This entity contains the sub-groups and entities that go in to a single location (e.g. datacenter)
 * to provide web-app cluster functionality, viz load-balancer (controller) and webapp software processes.
 * <p>
 * You can customise the web server by customising the memberSpec.
 * <p>
 * The children of this entity are:
 * <ul>
 * <li>a {@link brooklyn.entity.group.DynamicCluster} of {@link WebAppService}s (defaults to JBoss7Server)
 * <li>a cluster controller (defaulting to Nginx if none supplied)
 * </ul>
 * 
 * This entity is also a group whose members mirror those of the child DynamicCluster (so do not include the load balancer).
 * This is convenient for associating policies such as ServiceReplacer with this entity, rather 
 * than with the child {@link brooklyn.entity.group.DynamicCluster}. However, note that changing this entity's
 * members has no effect on the members of the underlying DynamicCluster - treat this as a read-only view.
 */
@ImplementedBy(ControlledDynamicWebAppClusterImpl.class)
public interface ControlledDynamicWebAppCluster extends DynamicGroup, Entity, Startable, Resizable, MemberReplaceable, Group, ElasticJavaWebAppService, JavaWebAppService.CanDeployAndUndeploy, JavaWebAppService.CanRedeployAll {
    
    @SetFromFlag("initialSize")
    public static ConfigKey<Integer> INITIAL_SIZE = ConfigKeys.newConfigKeyWithDefault(Cluster.INITIAL_SIZE, 1);

    @SetFromFlag("controller")
    public static BasicAttributeSensorAndConfigKey<LoadBalancer> CONTROLLER = new BasicAttributeSensorAndConfigKey<LoadBalancer>(
        LoadBalancer.class, "controlleddynamicwebappcluster.controller", "Controller for the cluster; if null a default will created (using controllerSpec)");

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SetFromFlag("controllerSpec")
    public static BasicAttributeSensorAndConfigKey<EntitySpec<? extends LoadBalancer>> CONTROLLER_SPEC = new BasicAttributeSensorAndConfigKey(
            EntitySpec.class, "controlleddynamicwebappcluster.controllerSpec", "Spec for creating the controller (if one not supplied explicitly); if null an NGINX instance will be created");

    @SuppressWarnings({ "unchecked", "rawtypes", "deprecation" })
    /** factory (or closure) to create the web server, given flags */
    @SetFromFlag("factory")
    public static BasicAttributeSensorAndConfigKey<ConfigurableEntityFactory<? extends WebAppService>> FACTORY = new BasicAttributeSensorAndConfigKey(
            ConfigurableEntityFactory.class, DynamicCluster.FACTORY.getName(), "factory (or closure) to create the web server");

    @SuppressWarnings({ "unchecked", "rawtypes" })
    /** Spec for web server entiites to be created */
    @SetFromFlag("memberSpec")
    public static BasicAttributeSensorAndConfigKey<EntitySpec<? extends WebAppService>> MEMBER_SPEC = new BasicAttributeSensorAndConfigKey(
            EntitySpec.class, DynamicCluster.MEMBER_SPEC.getName(), "Spec for web server entiites to be created");

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SetFromFlag("webClusterSpec")
    public static BasicAttributeSensorAndConfigKey<EntitySpec<? extends DynamicWebAppCluster>> WEB_CLUSTER_SPEC = new BasicAttributeSensorAndConfigKey(
            EntitySpec.class, "controlleddynamicwebappcluster.webClusterSpec", "Spec for creating the cluster; if null a DynamicWebAppCluster will be created");

    public static AttributeSensor<DynamicWebAppCluster> CLUSTER = new BasicAttributeSensor<DynamicWebAppCluster>(
            DynamicWebAppCluster.class, "controlleddynamicwebappcluster.cluster", "Underlying web-app cluster");

    public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;

    public static final AttributeSensor<Lifecycle> SERVICE_STATE_ACTUAL = Attributes.SERVICE_STATE_ACTUAL;

    
    public LoadBalancer getController();
    
    public ConfigurableEntityFactory<WebAppService> getFactory();
    
    public DynamicWebAppCluster getCluster();
    
}
