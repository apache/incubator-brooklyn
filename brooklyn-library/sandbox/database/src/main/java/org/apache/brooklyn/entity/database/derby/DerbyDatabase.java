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
package org.apache.brooklyn.entity.database.derby;

import java.util.Collection;
import java.util.Map;

import javax.management.ObjectName;

import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.entity.database.Database;
import org.apache.brooklyn.entity.database.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.apache.brooklyn.feed.jmx.JmxHelper;
import org.apache.brooklyn.entity.java.UsesJava;
import org.apache.brooklyn.entity.java.UsesJmx;
import org.apache.brooklyn.core.config.BasicConfigKey;
import org.apache.brooklyn.util.collections.MutableMap;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * An {@link Entity} that represents a single Derby SQL database server instance.
 *
 * TODO work in progress
 */
public class DerbyDatabase extends SoftwareProcessImpl implements Database, UsesJava, UsesJmx {
    private static final Logger log = LoggerFactory.getLogger(DerbyDatabase.class);

    @SetFromFlag("version")
    public static final ConfigKey<String> SUGGESTED_VERSION =
            ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "10.8.1.2");

    public static final PortAttributeSensorAndConfigKey JDBC_PORT = new PortAttributeSensorAndConfigKey(
            "derby.jdbcPort", "Suggested JDBC port");
    
    public static final ConfigKey<String> VIRTUAL_HOST_NAME = new BasicConfigKey<String>(
            String.class, "derby.virtualHost", "Derby virtual host name", "localhost");

    public static final BasicAttributeSensorAndConfigKey<String> JMX_USER = new BasicAttributeSensorAndConfigKey<String>(
            UsesJmx.JMX_USER, "admin");
    
    public static final BasicAttributeSensorAndConfigKey<String> JMX_PASSWORD = new BasicAttributeSensorAndConfigKey<String>(
            UsesJmx.JMX_PASSWORD, "admin");

    @SetFromFlag
    protected Collection<String> schemaNames;
    
    @SetFromFlag
    protected Map<String, DerbySchema> schemas;

    protected transient JmxHelper jmxHelper;
    
    public DerbyDatabase() {
        this(MutableMap.of(), null);
    }
    public DerbyDatabase(Map properties) {
        this(properties, null);
    }
    public DerbyDatabase(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public DerbyDatabase(Map properties, Entity parent) {
        super(properties, parent);

        if (schemaNames == null) schemaNames = Lists.newArrayList();
        if (schemas == null) schemas = Maps.newLinkedHashMap();
    }

    @Override
    public Class<? extends DerbyDatabaseDriver> getDriverInterface() {
        return DerbyDatabaseDriver.class;
    }

    @Override
    public void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
    }

    @Override
    public void postStart() {
        super.postStart();
        for (String name : schemaNames) {
            createSchema(name);
        }
    }

    @Override
    public void preStop() {
        super.preStop();
        for (DerbySchema schema : schemas.values()) {
            schema.destroy();
        }
        if (jmxHelper != null) jmxHelper.terminate();
    }

    public void createSchema(String name) {
        createSchema(name, ImmutableMap.of());
    }
    
    public void createSchema(String name, Map properties) {
        Map allprops = MutableMap.builder().putAll(properties).put("name", name).build();
        DerbySchema schema = new DerbySchema(allprops);
        schema.init();
        schema.create();
        schemas.put(name, schema);
    }

    public Collection<Schema> getSchemas() {
        return ImmutableList.<Schema>copyOf(schemas.values());
    }
    
    public void addSchema(Schema schema) {
        schemas.put(schema.getName(), (DerbySchema) schema);
    }
    
    public void removeSchema(String schemaName) {
        schemas.remove(schemaName);
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper().add("jdbcPort", getAttribute(JDBC_PORT));
    }

    protected boolean computeNodeUp() {
        // FIXME Use the JmxAdapter.reachable() stuff instead of getAttribute
        try {
            ObjectName serverInfoObjectName = ObjectName.getInstance("org.apache.derby:type=ServerInformation,name=ServerInformation");
            String productVersion = (String) jmxHelper.getAttribute(serverInfoObjectName, "ProductVersion");
            return (productVersion != null);
        } catch (Exception e) {
            return false;
        }
    }
}
