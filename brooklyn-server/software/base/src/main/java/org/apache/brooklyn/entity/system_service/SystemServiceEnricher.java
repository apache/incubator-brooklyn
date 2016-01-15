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
package org.apache.brooklyn.entity.system_service;

import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.mgmt.ExecutionContext;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.sensor.Enricher;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.EffectorTasks;
import org.apache.brooklyn.core.enricher.AbstractEnricher;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags.WrappedStream;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.core.task.BasicExecutionManager;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.TaskBuilder;
import org.apache.brooklyn.util.core.task.ssh.SshPutTaskWrapper;
import org.apache.brooklyn.util.core.task.ssh.SshTasks;
import org.apache.brooklyn.util.core.task.system.ProcessTaskFactory;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.util.net.Urls;

import com.google.common.collect.ImmutableSet;

public class SystemServiceEnricher extends AbstractEnricher implements Enricher {
    public static final String DEFAULT_ENRICHER_UNIQUE_TAG = "systemService.tag";
    protected static final Set<String> LAUNCH_EFFECTOR_NAMES = ImmutableSet.of("start", "restart");
    public static final ConfigKey<String> LAUNCH_SCRIPT_NAME = ConfigKeys.newStringConfigKey(
            "service.script_name", "The name of the launch script to be created in the runtime directory of the entity.", "service-launch.sh");
    public static final ConfigKey<String> SERVICE_NAME = ConfigKeys.newStringConfigKey(
            "service.name", "The name of the system service. Can use ${entity_name} and ${id} variables to template the value.", "${entity_name}-${id}");

    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        subscribeLaunch();
        uniqueTag = DEFAULT_ENRICHER_UNIQUE_TAG;
    }

    private void subscribeLaunch() {
        subscriptions().subscribe(entity, Attributes.SERVICE_STATE_ACTUAL, new EntityLaunchListener(this));
    }

    public void onLaunched(Task<?> task) {
        WrappedStream streamStdin = BrooklynTaskTags.stream(task, BrooklynTaskTags.STREAM_STDIN);
        if (streamStdin == null) return;

        WrappedStream streamEnv = BrooklynTaskTags.stream(task, BrooklynTaskTags.STREAM_ENV);
        String stdin = streamStdin.streamContents.get();
        String env = streamEnv.streamContents.get();

        final SshMachineLocation sshMachine = EffectorTasks.getSshMachine(entity);
        final String launchScriptPath = Urls.mergePaths(getRunDir(), getStartScriptName());

        Task<Void> installerTask = TaskBuilder.<Void>builder()
                .displayName("install (service)")
                .description("Install as a system service")
                .body(new Runnable() {
                    @Override
                    public void run() {
                        ProcessTaskFactory<Integer> taskFactory = SshTasks.newSshExecTaskFactory(sshMachine, "[ -e '" + launchScriptPath + "' ]")
                                .summary("check installed")
                                .allowingNonZeroExitCode();
                        boolean isInstalled = DynamicTasks.queue(taskFactory).get() == 0;
                        if (!isInstalled) {
                            Task<?> serviceInstallTask = SystemServiceInstallerFactory.of(entity, SystemServiceEnricher.this).getServiceInstallTask();
                            DynamicTasks.queue(serviceInstallTask);
                        }
                    }
                })
                .build();

        SshPutTaskWrapper updateLaunchScriptTask = SshTasks.newSshPutTaskFactory(sshMachine, launchScriptPath).contents(getLaunchScript(stdin, env)).newTask();
        ProcessTaskWrapper<Integer> makeExecutableTask = SshTasks.newSshExecTaskFactory(sshMachine, "chmod +x " + launchScriptPath)
                .requiringExitCodeZero()
                .newTask();
        Task<Void> udpateTask = TaskBuilder.<Void>builder()
                .displayName("update-launch")
                .description("Update launch script used by the system service")
                .add(updateLaunchScriptTask)
                .add(makeExecutableTask)
                .build();

        Task<Void> updateService = TaskBuilder.<Void>builder()
                .displayName("update-system-service")
                .description("Update system service")
                .add(installerTask)
                .add(udpateTask)
                .tag(BrooklynTaskTags.tagForContextEntity(entity))
                .tag(BrooklynTaskTags.NON_TRANSIENT_TASK_TAG)
                .build();

        DynamicTasks.submitTopLevelTask(updateService, entity);
    }

    private String getLaunchScript(String stdin, String env) {
        // (?m) - multiline enable
        // insert export at beginning of each line
        return env.replaceAll("(?m)^", "export ") + "\n" + stdin;
    }

    private String getRunDir() {
        return entity.getAttribute(SoftwareProcess.RUN_DIR);
    }

    private String getStartScriptName() {
        return config().get(LAUNCH_SCRIPT_NAME);
    }

    ExecutionContext getEntityExecutionContext() {
        return getManagementContext().getExecutionContext(entity);
    }

    protected Entity getEntity() {
        return entity;
    }

}
