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
package org.apache.brooklyn.entity.monitoring.monit;

import java.util.Map;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.objs.HasShortName;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey.StringAttributeSensorAndConfigKey;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

@Catalog(name="Monit Node", description="Monit is a free open source utility for managing and monitoring, processes, programs, files, directories and filesystems on a UNIX system")
@ImplementedBy(MonitNodeImpl.class)
public interface MonitNode extends SoftwareProcess, HasShortName {
    // e.g. https://mmonit.com/monit/dist/binary/5.6/monit-5.6-linux-x64.tar.gz
    @SetFromFlag("downloadUrl")
    public static final BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new StringAttributeSensorAndConfigKey(
            Attributes.DOWNLOAD_URL, "https://mmonit.com/monit/dist/binary/${version}/monit-${version}-${driver.osTag}.tar.gz");
    
    @SetFromFlag("version")
    public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "5.9");
    
    @SetFromFlag("controlFileUrl")
    public static final ConfigKey<String> CONTROL_FILE_URL = ConfigKeys.newStringConfigKey("monit.control.url", "URL where monit control (.monitrc) file can be found", "");

    @SuppressWarnings("serial")
    public static final ConfigKey<Map<String, Object>> CONTROL_FILE_SUBSTITUTIONS = ConfigKeys.newConfigKey(new TypeToken<Map<String, Object>>(){}, "monit.control.substitutions", 
        "Additional substitutions to be used in the control file template", ImmutableMap.<String, Object>of());
    
    public static final AttributeSensor<String> MONIT_TARGET_PROCESS_NAME = Sensors.newStringSensor("monit.target.process.name");
    
    public static final AttributeSensor<String> MONIT_TARGET_PROCESS_STATUS = Sensors.newStringSensor("monit.target.process.status");
}
