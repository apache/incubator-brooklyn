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
package org.apache.brooklyn.entity.machine;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.util.time.Duration;

@Catalog(name="Machine Entity", description="Represents a machine, providing metrics about it (e.g. obtained from ssh)")
@ImplementedBy(MachineEntityImpl.class)
public interface MachineEntity extends EmptySoftwareProcess {

    AttributeSensor<Duration> UPTIME = MachineAttributes.UPTIME;
    AttributeSensor<Double> LOAD_AVERAGE = MachineAttributes.LOAD_AVERAGE;
    AttributeSensor<Double> CPU_USAGE = MachineAttributes.CPU_USAGE;
    AttributeSensor<Long> FREE_MEMORY = MachineAttributes.FREE_MEMORY;
    AttributeSensor<Long> TOTAL_MEMORY = MachineAttributes.TOTAL_MEMORY;
    AttributeSensor<Long> USED_MEMORY = MachineAttributes.USED_MEMORY;

    MethodEffector<String> EXEC_COMMAND = new MethodEffector<String>(MachineEntity.class, "execCommand");
    MethodEffector<String> EXEC_COMMAND_TIMEOUT = new MethodEffector<String>(MachineEntity.class, "execCommandTimeout");

    /**
     * Execute a command and return the output.
     */
    @Effector(description = "Execute a command and return the output")
    String execCommand(
            @EffectorParam(name = "command", description = "Command") String command);

    /**
     * Execute a command and return the output, or throw an exception after a timeout.
     */
    @Effector(description = "Execute a command and return the output")
    String execCommandTimeout(
            @EffectorParam(name = "command", description = "Command") String command,
            @EffectorParam(name = "timeout", description = "Timeout") Duration timeout);

}
