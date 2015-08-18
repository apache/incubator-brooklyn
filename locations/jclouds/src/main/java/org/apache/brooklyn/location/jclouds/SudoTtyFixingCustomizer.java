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
package org.apache.brooklyn.location.jclouds;

import org.apache.brooklyn.location.basic.SshMachineLocation;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.ssh.SshTasks;
import org.apache.brooklyn.util.core.task.ssh.SshTasks.OnFailingTask;
import org.jclouds.compute.ComputeService;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

/**
 * Wraps Brooklyn's sudo-tty mitigations in a {@link JcloudsLocationCustomizer} for easy(-ish) consumption
 * in YAML blueprints:
 *
 * <pre>
 *   name: My App
 *   brooklyn.config:
 *     provisioning.properties:
 *       customizerType: SudoTtyFixingCustomizer
 *   services: ...
 * </pre>
 *
 * <p>This class should be seen as a temporary workaround and might disappear completely if/when Brooklyn takes care of this automatically.
 *
 * <p>See
 * <a href='http://unix.stackexchange.com/questions/122616/why-do-i-need-a-tty-to-run-sudo-if-i-can-sudo-without-a-password'>http://unix.stackexchange.com/questions/122616/why-do-i-need-a-tty-to-run-sudo-if-i-can-sudo-without-a-password</a>
 * for background.
 */
@Beta
public class SudoTtyFixingCustomizer extends BasicJcloudsLocationCustomizer {

    @Override
    public void customize(JcloudsLocation location, ComputeService computeService, JcloudsMachineLocation machine) {
        Preconditions.checkArgument(machine instanceof SshMachineLocation, "machine must be SshMachineLocation, but is %s", machine.getClass());
        DynamicTasks.queueIfPossible(SshTasks.dontRequireTtyForSudo((SshMachineLocation)machine, OnFailingTask.FAIL)).orSubmitAndBlock();
        DynamicTasks.waitForLast();
    }
}
