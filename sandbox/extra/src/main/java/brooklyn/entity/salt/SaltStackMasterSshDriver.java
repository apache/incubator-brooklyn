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
package brooklyn.entity.salt;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;

import com.google.common.collect.ImmutableMap;

public class SaltStackMasterSshDriver extends JavaSoftwareProcessSshDriver implements SaltStackMasterDriver {

    public SaltStackMasterSshDriver(SaltStackMasterImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public SaltStackMasterImpl getEntity() {
        return (SaltStackMasterImpl) super.getEntity();
    }

    @Override
    protected String getLogFileLocation() {
        return "master.log";
    }

    private String getPidFile() {
        return "master.pid";
    }

    @Override
    public void install() {
        String url = Entities.getRequiredUrlConfig(getEntity(), SaltStackMaster.BOOTSTRAP_URL);
        copyTemplate(url, "/etc/salt/master");

        // Copy the file contents to the remote machine
//        DynamicTasks.queue(SshEffectorTasks.put("/tmp/cumulus.yaml").contents(contents)).get();

        // Run Salt bootstrap task to install master
        DynamicTasks.queue(SaltTasks.installSaltMaster(getEntity(), getRunDir(), true));


        newScript("createInstallDir")
                .body.append("mkdir -p "+getInstallDir())
                .failOnNonZeroResultCode()
                .execute();

        newScript(INSTALLING).
                failOnNonZeroResultCode().
                body.append("").execute();
    }

    @Override
    public void customize() {
    }

    @Override
    public void launch() {
        newScript(ImmutableMap.of("usePidFile", false), LAUNCHING)
                .body.append(BashCommands.sudo("start salt-master"))
                .execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(ImmutableMap.of("usePidFile", false), CHECK_RUNNING)
                .body.append(BashCommands.sudo("status salt-master"))
                .execute() == 0;
    }

    @Override
    public void stop() {
        newScript(ImmutableMap.of("usePidFile", false), STOPPING)
                .body.append(BashCommands.sudo("stop salt-master"))
                .execute();
    }
}
