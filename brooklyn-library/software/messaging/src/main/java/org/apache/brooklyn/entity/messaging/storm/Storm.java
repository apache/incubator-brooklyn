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
package org.apache.brooklyn.entity.messaging.storm;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.java.UsesJmx;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.zookeeper.ZooKeeperEnsemble;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 * An {@link org.apache.brooklyn.api.entity.Entity} that represents a Storm node (UI, Nimbus or Supervisor).
 */
@Catalog(name="Storm Node", description="Apache Storm is a distributed realtime computation system. "
        + "Storm makes it easy to reliably process unbounded streams of data, doing for realtime processing "
        + "what Hadoop did for batch processing")
@ImplementedBy(StormImpl.class)
public interface Storm extends SoftwareProcess, UsesJmx {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "0.8.2");

    @SetFromFlag("nimbusHostname")
    ConfigKey<String> NIMBUS_HOSTNAME = ConfigKeys.newStringConfigKey("storm.nimbus.hostname");
    
    @SetFromFlag("nimbusEntity")
    ConfigKey<Entity> NIMBUS_ENTITY = ConfigKeys.newConfigKey(Entity.class, "storm.nimbus.entity");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "https://dl.dropboxusercontent.com/s/fl4kr7w0oc8ihdw/storm-${version}.zip");

    ConfigKey<Object> START_MUTEX = ConfigKeys.newConfigKey(Object.class, "storm.start.mutex");

    @SetFromFlag("role")
    ConfigKey<Role> ROLE = ConfigKeys.newConfigKey(Role.class, "storm.role", "The Storm server role");

    @SetFromFlag("localDir")
    ConfigKey<String> LOCAL_DIR = ConfigKeys.newStringConfigKey("storm.local.dir", "Setting for Storm local dir");
    
    @SetFromFlag("uiPort")
    PortAttributeSensorAndConfigKey UI_PORT = new PortAttributeSensorAndConfigKey("storm.ui.port", "Storm UI port", "8080+");

    @SetFromFlag("thriftPort")
    PortAttributeSensorAndConfigKey THRIFT_PORT = new PortAttributeSensorAndConfigKey("storm.thrift.port", "Storm Thrift port", "6627");

    @SetFromFlag("zookeeperEnsemble")
    ConfigKey<ZooKeeperEnsemble> ZOOKEEPER_ENSEMBLE = ConfigKeys.newConfigKey(ZooKeeperEnsemble.class,
            "storm.zookeeper.ensemble", "Zookeeper ensemble entity");

    @SetFromFlag("stormConfigTemplateUrl")
    ConfigKey<String> STORM_CONFIG_TEMPLATE_URL = ConfigKeys.newStringConfigKey("storm.config.templateUrl",
            "Template file (in freemarker format) for the storm.yaml config file",
            "classpath://org/apache/brooklyn/entity/messaging/storm/storm.yaml");

    @SetFromFlag("zeromqVersion")
    ConfigKey<String> ZEROMQ_VERSION = ConfigKeys.newStringConfigKey("storm.zeromq.version", "zeromq version", "2.1.7");

    AttributeSensor<Boolean> SERVICE_UP_JMX = Sensors.newBooleanSensor("storm.service.jmx.up", "Whether JMX is up for this service");

    String getStormConfigTemplateUrl();

    String getHostname();

    Role getRole();
    
    enum Role { NIMBUS, SUPERVISOR, UI }

    AttributeSensor<String> STORM_UI_URL = StormUiUrl.STORM_UI_URL;
    
    class StormUiUrl {
        public static final AttributeSensor<String> STORM_UI_URL = Sensors.newStringSensor("storm.ui.url", "URL");

        static {
            RendererHints.register(STORM_UI_URL, RendererHints.namedActionWithUrl());
        }
    }

}
