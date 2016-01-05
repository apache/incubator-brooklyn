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
package org.apache.brooklyn.entity.osgi.karaf;

import java.net.URISyntaxException;
import java.util.Map;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.config.MapConfigKey;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.core.sensor.BasicAttributeSensor;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.entity.java.UsesJava;
import org.apache.brooklyn.entity.java.UsesJmx;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 * This sets up a Karaf OSGi container
 */
@Catalog(name="Karaf", description="Apache Karaf is a small OSGi based runtime which provides a lightweight container onto which various components and applications can be deployed.", iconUrl="classpath:///karaf-logo.png")
@ImplementedBy(KarafContainerImpl.class)
public interface KarafContainer extends SoftwareProcess, UsesJava, UsesJmx {
    
    // TODO Better way of setting/overriding defaults for config keys that are defined in super class SoftwareProcess

    public static final String WRAP_SCHEME = "wrap";
    public static final String FILE_SCHEME = "file";
    public static final String MVN_SCHEME = "mvn";
    public static final String HTTP_SCHEME = "http";

    public static final MethodEffector<Map<Long,Map<String,?>>> LIST_BUNDLES = new MethodEffector(KarafContainer.class, "listBundles");
    public static final MethodEffector<Long> INSTALL_BUNDLE = new MethodEffector<Long>(KarafContainer.class, "installBundle");
    public static final MethodEffector<Void> UNINSTALL_BUNDLE = new MethodEffector<Void>(KarafContainer.class, "uninstallBundle");
    public static final MethodEffector<Void> INSTALL_FEATURE = new MethodEffector<Void>(KarafContainer.class, "installFeature");
    public static final MethodEffector<Void> UPDATE_SERVICE_PROPERTIES = new MethodEffector<Void>(KarafContainer.class, "updateServiceProperties");

    @SetFromFlag("version")
    public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(
            SoftwareProcess.SUGGESTED_VERSION, "2.3.0");

    @SetFromFlag("downloadUrl")
    public static final BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://apache.mirror.anlx.net/karaf/${version}/apache-karaf-${version}.tar.gz");

    @SetFromFlag("karafName")
    public static final BasicAttributeSensorAndConfigKey<String> KARAF_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "karaf.name", "Karaf instance name", "root");

    // TODO too complicated? Used by KarafContainer; was in JavaApp; where should it be in brave new world?
    public static final MapConfigKey<Map<String,String>> NAMED_PROPERTY_FILES = new MapConfigKey(
            Map.class, "karaf.runtime.files", "Property files to be generated, referenced by name relative to runDir");

    @SetFromFlag("jmxUser")
    public static final BasicAttributeSensorAndConfigKey<String> JMX_USER = new BasicAttributeSensorAndConfigKey<String>(
            UsesJmx.JMX_USER, "karaf");
    
    @SetFromFlag("jmxPassword")
    public static final BasicAttributeSensorAndConfigKey<String> JMX_PASSWORD = new BasicAttributeSensorAndConfigKey<String>(
            UsesJmx.JMX_PASSWORD, "karaf");
        
    @SetFromFlag("jmxPort")
    public static final PortAttributeSensorAndConfigKey JMX_PORT = new PortAttributeSensorAndConfigKey(
            UsesJmx.JMX_PORT, "44444+");

    @SetFromFlag("rmiRegistryPort")
    public static final PortAttributeSensorAndConfigKey RMI_REGISTRY_PORT = UsesJmx.RMI_REGISTRY_PORT;
    
    @SetFromFlag("jmxContext")
    public static final BasicAttributeSensorAndConfigKey<String> JMX_CONTEXT = new BasicAttributeSensorAndConfigKey<String>(
            UsesJmx.JMX_CONTEXT, "karaf-"+KARAF_NAME.getConfigKey().getDefaultValue());

    public static final BasicAttributeSensor<Map> KARAF_INSTANCES = new BasicAttributeSensor<Map>(
            Map.class, "karaf.admin.instances", "Karaf admin instances");
    public static final BasicAttributeSensor<Boolean> KARAF_ROOT = new BasicAttributeSensor<Boolean>(
            Boolean.class, "karaf.admin.isRoot", "Karaf admin isRoot");
    public static final BasicAttributeSensor<String> KARAF_JAVA_OPTS = new BasicAttributeSensor<String>(
            String.class, "karaf.admin.java_opts", "Karaf Java opts");
    public static final BasicAttributeSensor<String> KARAF_INSTALL_LOCATION  = new BasicAttributeSensor<String>(
            String.class, "karaf.admin.location", "Karaf install location");
    public static final BasicAttributeSensor<Integer> KARAF_PID = new BasicAttributeSensor<Integer>(
            Integer.class, "karaf.admin.pid", "Karaf instance PID");
    public static final BasicAttributeSensor<Integer> KARAF_SSH_PORT = new BasicAttributeSensor<Integer>(
            Integer.class, "karaf.admin.ssh_port", "Karaf SSH Port");
    public static final BasicAttributeSensor<Integer> KARAF_RMI_REGISTRY_PORT = new BasicAttributeSensor<Integer>(
            Integer.class, "karaf.admin.rmi_registry_port", "Karaf instance RMI registry port");
    public static final BasicAttributeSensor<Integer> KARAF_RMI_SERVER_PORT = new BasicAttributeSensor<Integer>(
            Integer.class, "karaf.admin.rmi_server_port", "Karaf RMI (JMX) server port");
    public static final BasicAttributeSensor<String> KARAF_STATE = new BasicAttributeSensor<String>(
            String.class, "karaf.admin.state", "Karaf instance state");

    @Effector(description="Updates the OSGi Service's properties, adding (and overriding) the given key-value pairs")
    public void updateServiceProperties(
            @EffectorParam(name="serviceName", description="Name of the OSGi service") String serviceName, 
            Map<String,String> additionalVals);
    
    @Effector(description="Installs the given OSGi feature")
    public void installFeature(
            @EffectorParam(name="featureName", description="Name of the feature - see org.apache.karaf:type=features#installFeature()") final String featureName) 
            throws Exception;

    @Effector(description="Lists all the karaf bundles")
    public Map<Long,Map<String,?>> listBundles();
    
    /**
     * throws URISyntaxException If bundle name is not a valid URI
     */
    @Effector(description="Deploys the given bundle, returning the bundle id - see osgi.core:type=framework#installBundle()")
    public long installBundle(
            @EffectorParam(name="bundle", description="URI of bundle to be deployed") String bundle) throws URISyntaxException;

    @Effector(description="Undeploys the bundle with the given id")
    public void uninstallBundle(
            @EffectorParam(name="bundleId", description="Id of the bundle") Long bundleId);
}
