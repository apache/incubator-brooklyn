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
package brooklyn.entity.nosql.hazelcast;

import static java.lang.String.format;
import java.util.List;
import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;

import com.google.common.collect.ImmutableList;

public class HazelcastNodeSshDriver extends AbstractSoftwareProcessSshDriver implements HazelcastNodeDriver {

    public HazelcastNodeSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
    }

    @Override
    public void install() {
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        
        List<String> commands = ImmutableList.<String>builder()
            .add(BashCommands.installJavaLatestOrWarn())
            .addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs))
            .build();
        
        newScript(INSTALLING).body.append(commands).execute();
    }

    @Override
    public void customize() {

    	ImmutableList.Builder<String> commands = new ImmutableList.Builder<String>()
                .add("mkdir -p lib conf log")
                .add(String.format("cp %s/%s %s/lib/", getInstallDir(), resolver.getFilename(), getRunDir()));

        newScript(CUSTOMIZING)
                .body.append(commands.build())
                .failOnNonZeroResultCode()
                .execute();
        
        copyTemplate(entity.getConfig(HazelcastNode.TEMPLATE_CONFIGURATION_URL), getConfigFile());
        
    }

    @Override
    public void launch() {
        
        entity.setAttribute(HazelcastNode.PID_FILE, Os.mergePathsUnix(getRunDir(), PID_FILENAME));
        
        StringBuilder commandBuilder = new StringBuilder()
            .append(format("nohup java -cp ./lib/%s", resolver.getFilename()))
            .append(" -Dhazelcast.config=" + getConfigFile())
            .append(" com.hazelcast.core.server.StartServer >> ./log/out.log 2>&1 </dev/null &");
        
        newScript(MutableMap.of(USE_PID_FILE, true), LAUNCHING)
            .updateTaskAndFailOnNonZeroResultCode()
            .body.append(commandBuilder.toString())
            .execute();
    }
       
    public String getConfigFile() {
        return Os.mergePathsUnix(getRunDir(), "conf/hazelcast.xml");
    }
    
    @Override
    public boolean isRunning() {       
        return newScript(MutableMap.of(USE_PID_FILE, true), CHECK_RUNNING).execute() == 0;
    }
    
    @Override
    public void stop() {
        newScript(MutableMap.of(USE_PID_FILE, true), STOPPING).execute();
    }
    
    @Override
    public void kill() {
        newScript(MutableMap.of(USE_PID_FILE, true), KILLING).execute();
    }

}
