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
package brooklyn.entity.nosql.infinispan;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.java.UsesJmx;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;

/**
 * An {@link brooklyn.entity.Entity} that represents an Infinispan service
 */
public class Infinispan5Server extends SoftwareProcessImpl implements UsesJmx {
    private static final Logger log = LoggerFactory.getLogger(Infinispan5Server.class);
    
    public static final BasicAttributeSensorAndConfigKey<String> PROTOCOL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "infinispan.server.protocol", 
            "Infinispan protocol (e.g. memcached, hotrod, or websocket)", "memcached");
    
    public static final PortAttributeSensorAndConfigKey PORT = new PortAttributeSensorAndConfigKey(
            "infinispan.server.port", "TCP port number to listen on");

    @SetFromFlag("version")
    public static final ConfigKey<String> SUGGESTED_VERSION =
            ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "5.0.0.CR8");

    // Default filename is "infinispan-${version}-all.zip"
    @SetFromFlag("downloadUrl")
    public static final BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://sourceforge.net/projects/infinispan/files/infinispan/${version}/infinispan-${version}-all.zip/download");

    public Infinispan5Server() {
        this(MutableMap.of(), null);
    }
    public Infinispan5Server(Map properties) {
        this(properties, null);
    }
    public Infinispan5Server(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public Infinispan5Server(Map properties, Entity parent) {
        super(properties, parent);
    }

    @Override
    public Class getDriverInterface() {
        return Infinispan5Driver.class;
    }

    @Override
    protected void connectSensors() {
		super.connectSensors();
		super.connectServiceUpIsRunning();
    }
    
    @Override
    protected void disconnectSensors() {
        super.disconnectServiceUpIsRunning();
        super.disconnectSensors();
    }
}
