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
package org.apache.brooklyn.entity.salt;

import java.util.List;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.util.core.flags.SetFromFlag;


import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.sensor.BasicAttributeSensor;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;

import com.google.common.reflect.TypeToken;

@ImplementedBy(SaltStackMasterImpl.class)
@Catalog(name="SaltStack Master", description="The Salt master server")
public interface SaltStackMaster extends SoftwareProcess {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.SUGGESTED_VERSION, "stable");

    @SetFromFlag("bootstrapUrl")
    ConfigKey<String> BOOTSTRAP_URL = ConfigKeys.newStringConfigKey(
            "salt.bootstrap.url", "The URL that returns the Salt boostrap commands",
            "http://bootstrap.saltstack.org/");

    @SetFromFlag("masterUser")
    ConfigKey<String> MASTER_USER = ConfigKeys.newStringConfigKey(
            "salt.master.user", "The user that runs the Salt master daemon process",
            "root");

    @SetFromFlag("masterConfigTemplate")
    ConfigKey<String> MASTER_CONFIG_TEMPLATE_URL = ConfigKeys.newStringConfigKey(
            "salt.master.config.templateUrl", "The template for the Salt master configuration (URL)",
            "classpath://org/apache/brooklyn/entity/salt/master");

    @SetFromFlag("saltPort")
    PortAttributeSensorAndConfigKey SALT_PORT = new PortAttributeSensorAndConfigKey(
            "salt.port", "Port used for communication between Salt master and minion processes", "4506+");

    @SetFromFlag("publishPort")
    PortAttributeSensorAndConfigKey PUBLISH_PORT = new PortAttributeSensorAndConfigKey(
            "salt.publish.port", "Port used by the Salt master publisher", "4505+");

    @SuppressWarnings("serial")
    AttributeSensor<List<String>> MINION_IDS = new BasicAttributeSensor<List<String>>(new TypeToken<List<String>>() {},
            "salt.minions", "List of Salt minion IDs");

}
