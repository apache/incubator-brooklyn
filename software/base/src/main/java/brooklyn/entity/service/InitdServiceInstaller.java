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
package brooklyn.entity.service;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.trait.HasShortName;
import org.apache.brooklyn.api.management.Task;
import org.apache.brooklyn.api.policy.Enricher;
import org.apache.brooklyn.core.util.ResourceUtils;
import org.apache.brooklyn.core.util.task.Tasks;
import org.apache.brooklyn.core.util.task.ssh.SshPutTaskWrapper;
import org.apache.brooklyn.core.util.task.ssh.SshTasks;
import org.apache.brooklyn.core.util.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.core.util.text.TemplateProcessor;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.effector.EffectorTasks;

import org.apache.brooklyn.location.basic.SshMachineLocation;
import org.apache.brooklyn.location.cloud.names.AbstractCloudMachineNamer;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;


public class InitdServiceInstaller implements SystemServiceInstaller {
    private static final ConfigKey<String> SERVICE_TEMPLATE = ConfigKeys.newStringConfigKey(
            "service.initd.service_template", "URL of the template to be used as the /etc/init.d service", "classpath:///brooklyn/entity/service/service.sh");

    private final Entity entity;
    private final Enricher enricher;

    public InitdServiceInstaller(Entity entity, Enricher enricher) {
        this.entity = checkNotNull(entity, "entity");
        this.enricher = checkNotNull(enricher, "enricher");
    }

    @Override
    public Task<?> getServiceInstallTask() {
        ResourceUtils resource = new ResourceUtils(this);
        String pidFile = entity.getAttribute(SoftwareProcess.PID_FILE);
        String template = resource.getResourceAsString(enricher.config().get(SERVICE_TEMPLATE));
        String serviceName = getServiceName();
        SshMachineLocation sshMachine = EffectorTasks.getSshMachine(entity);
        Map<String, Object> params = MutableMap.<String, Object>of(
                "service.launch_script", Os.mergePaths(getRunDir(), getStartScriptName()),
                "service.name", serviceName,
                "service.user", sshMachine.getUser(),
                "service.log_path", getLogLocation());
        if (pidFile != null) {
            params.put("service.pid_file", pidFile);
        }
        String service = TemplateProcessor.processTemplateContents(template, (EntityInternal)entity, params);
        String tmpServicePath = Os.mergePaths(getRunDir(), serviceName);
        String servicePath = "/etc/init.d/" + serviceName;
        SshPutTaskWrapper putServiceTask = SshTasks.newSshPutTaskFactory(sshMachine, tmpServicePath)
                .contents(service)
                .newTask();
        ProcessTaskWrapper<Integer> installServiceTask = SshTasks.newSshExecTaskFactory(sshMachine,
                BashCommands.chain(
                    BashCommands.sudo("mv " + tmpServicePath + " " + servicePath),
                    BashCommands.sudo("chmod 0755 " + servicePath),
                    BashCommands.sudo("chkconfig --add " + serviceName),
                    BashCommands.sudo("chkconfig " + serviceName + " on")))
            .requiringExitCodeZero()
            .newTask();

        return Tasks.<Void>builder()
            .name("install (init.d)")
            .description("Install init.d service")
            .add(putServiceTask)
            .add(installServiceTask)
            .build();
    }

    private String getServiceName() {
        String serviceNameTemplate = enricher.config().get(SystemServiceEnricher.SERVICE_NAME);
        return serviceNameTemplate
                .replace("${id}", entity.getId())
                .replace("${entity_name}", getEntityName());
    }

    private CharSequence getEntityName() {
        String name;
        if (entity instanceof HasShortName) {
            name = ((HasShortName)entity).getShortName();
        } else if (entity instanceof Entity) {
            name = ((Entity)entity).getDisplayName();
        } else {
            name = "brooklyn-service";
        }
        return AbstractCloudMachineNamer.sanitize(name.toString()).toLowerCase();
    }

    private String getStartScriptName() {
        return enricher.config().get(SystemServiceEnricher.LAUNCH_SCRIPT_NAME);
    }

    private String getRunDir() {
        return entity.getAttribute(SoftwareProcess.RUN_DIR);
    }

    private String getLogLocation() {
        String logFileLocation = entity.getAttribute(Attributes.LOG_FILE_LOCATION);
        if (logFileLocation != null) {
            return new File(logFileLocation).getParent();
        } else {
            return "/tmp";
        }
    }

}
