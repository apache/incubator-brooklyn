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
package org.apache.brooklyn.entity.nosql.hazelcast;

import static java.lang.String.format;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.core.entity.Entities;

import org.apache.brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class HazelcastNodeSshDriver extends JavaSoftwareProcessSshDriver implements HazelcastNodeDriver {
    
    private static final Logger LOG = LoggerFactory.getLogger(HazelcastNodeSshDriver.class);

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
        if (LOG.isInfoEnabled()) {
            LOG.info("Customizing {}", entity.getAttribute(HazelcastNode.NODE_NAME));
        }
        
        ImmutableList.Builder<String> commands = new ImmutableList.Builder<String>()
                .add("mkdir -p lib conf log")
                .add(String.format("cp %s/%s %s/lib/", getInstallDir(), resolver.getFilename(), getRunDir()));

        newScript(CUSTOMIZING)
                .body.append(commands.build())
                .failOnNonZeroResultCode()
                .execute();
        
        copyTemplate(entity.getConfig(HazelcastNode.CONFIG_TEMPLATE_URL), Os.mergePathsUnix(getRunDir(), "conf", getConfigFileName()));
        
    }

    @Override
    public void launch() {
        
        entity.sensors().set(HazelcastNode.PID_FILE, Os.mergePathsUnix(getRunDir(), PID_FILENAME));
        
        String maxHeapMemorySize = getHeapMemorySize();
        
        if (LOG.isInfoEnabled()) {
            LOG.info("Launching {} with heap memory of {}", entity, maxHeapMemorySize);
        }
        
        // Setting initial heap size (Xms) size to match max heap size (Xms) at first
        String initialHeapMemorySize = maxHeapMemorySize;
        
        StringBuilder commandBuilder = new StringBuilder()
            .append(format("nohup java -cp ./lib/%s", resolver.getFilename()))
            .append(format(" -Xmx%s -Xms%s", maxHeapMemorySize, initialHeapMemorySize))
            .append(format(" -Dhazelcast.config=./conf/%s", getConfigFileName()))
            .append(format(" com.hazelcast.core.server.StartServer >> %s 2>&1 </dev/null &", getLogFileLocation()));
        
        newScript(MutableMap.of(USE_PID_FILE, true), LAUNCHING)
            .updateTaskAndFailOnNonZeroResultCode()
            .body.append(commandBuilder.toString())
            .execute();
    }
       
    public String getConfigFileName() {
        return entity.getConfig(HazelcastNode.CONFIG_FILE_NAME);
    }
    
    public String getHeapMemorySize() {
        return entity.getConfig(HazelcastNode.NODE_HEAP_MEMORY_SIZE);
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

    public List<String> getHazelcastNodesList() throws ExecutionException, InterruptedException {
        HazelcastCluster cluster = (HazelcastCluster) entity.getParent();
        List<String> result = Lists.newArrayList();

        for (Entity member : cluster.getMembers()) {
            String address = Entities.attributeSupplierWhenReady(member, HazelcastNode.SUBNET_ADDRESS).get();
            Integer port = Entities.attributeSupplierWhenReady(member, HazelcastNode.NODE_PORT).get();
            
            String addressAndPort = String.format("%s:%d", address, port);
            
            if (LOG.isInfoEnabled()) {
                LOG.info("Adding {} to the members' list of {}", addressAndPort, entity.getAttribute(HazelcastNode.NODE_NAME));
            }
            result.add(addressAndPort);
        }
        
        return result;
    }

    @Override
    protected String getLogFileLocation() {
        return Os.mergePathsUnix(getRunDir(),"/log/out.log");
    }
    
}
