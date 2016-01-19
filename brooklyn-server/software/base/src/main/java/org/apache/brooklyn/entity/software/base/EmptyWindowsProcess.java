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
package org.apache.brooklyn.entity.software.base;

import java.util.Collection;

import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;

import com.google.common.collect.ImmutableSet;

@ImplementedBy(EmptyWindowsProcessImpl.class)
public interface EmptyWindowsProcess extends SoftwareProcess {

    // 3389 is RDP; 5985 is WinRM (3389 isn't used by Brooklyn, but useful for the end-user subsequently)
    ConfigKey<Collection<Integer>> REQUIRED_OPEN_LOGIN_PORTS = ConfigKeys.newConfigKeyWithDefault(
            SoftwareProcess.REQUIRED_OPEN_LOGIN_PORTS,
            ImmutableSet.of(5985, 3389));
    
    ConfigKey<Boolean> USE_WINRM_MONITORING = ConfigKeys.newConfigKey("winrmMonitoring.enabled", "WinRM monitoring enabled", Boolean.TRUE);
}
