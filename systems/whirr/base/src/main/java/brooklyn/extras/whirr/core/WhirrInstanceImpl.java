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
package brooklyn.extras.whirr.core;

import java.io.IOException;

import org.apache.whirr.Cluster;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractGroupImpl;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.trait.Changeable;
import brooklyn.event.AttributeSensor;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;

public class WhirrInstanceImpl extends AbstractGroupImpl implements WhirrInstance {

    @SetFromFlag("role")
    public static final ConfigKey<String> ROLE = ConfigKeys.newStringConfigKey("whirr.instance.role", "Apache Whirr instance role");

    @SetFromFlag("instance")
    public static final ConfigKey<Cluster.Instance> INSTANCE = ConfigKeys.newConfigKey(Cluster.Instance.class, "whirr.instance.instance", "Apache Whirr instance Cluster.Instance");
        
    public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;

    public WhirrInstanceImpl() {
        super();
    }
    
    @Override
    public void init() {
        setAttribute(Changeable.GROUP_SIZE, 0);
        Cluster.Instance instance = getConfig(INSTANCE);
        if (instance != null) {
            try {
                setAttribute(HOSTNAME, instance.getPublicHostName());
            } catch (IOException e) {
                throw Exceptions.propagate(e);
            }
        }
    }
    
    @Override
    public String getRole() {
        return getConfig(ROLE);
    }
}
