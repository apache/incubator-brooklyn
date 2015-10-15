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

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.Callable;

import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.util.stream.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.core.task.ssh.SshTasks;
import org.apache.brooklyn.util.net.Protocol;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.ssh.IptablesCommands;
import org.apache.brooklyn.util.ssh.IptablesCommands.Chain;
import org.apache.brooklyn.util.ssh.IptablesCommands.Policy;
import org.apache.brooklyn.util.text.Strings;

/**
 * 
 */
@Beta
public class MachineInitTasks {

    // TODO Move somewhere so code can also be called by JcloudsLocation!
    
    private static final Logger log = LoggerFactory.getLogger(MachineInitTasks.class);

    protected EntityInternal entity() {
        return (EntityInternal) BrooklynTaskTags.getTargetOrContextEntity(Tasks.current());
    }

    /**
     * Returns a queued {@link Task} which opens the given ports in iptables on the given machine.
     */
    public Task<Void> openIptablesAsync(final Iterable<Integer> inboundPorts, final SshMachineLocation machine) {
        return DynamicTasks.queue("open iptables "+toTruncatedString(inboundPorts, 6), new Callable<Void>() {
            public Void call() {
                openIptablesImpl(inboundPorts, machine);
                return null;
            }
        });
    }

    /**
     * Returns a queued {@link Task} which stops iptables on the given machine.
     */
    public Task<Void> stopIptablesAsync(final SshMachineLocation machine) {
        return DynamicTasks.queue("stop iptables", new Callable<Void>() {
            public Void call() {
                stopIptablesImpl(machine);
                return null;
            }
        });
    }

    /**
     * See docs in {@link BashCommands#dontRequireTtyForSudo()}
     */
    public Task<Boolean> dontRequireTtyForSudoAsync(final SshMachineLocation machine) {
        return DynamicTasks.queue(SshTasks.dontRequireTtyForSudo(machine, true).newTask().asTask());
    }

    protected void openIptablesImpl(Iterable<Integer> inboundPorts, SshMachineLocation machine) {
        if (inboundPorts == null || Iterables.isEmpty(inboundPorts)) {
            log.info("No ports to open in iptables (no inbound ports) for {} at {}", machine, this);
        } else {
            log.info("Opening ports in iptables for {} at {}", entity(), machine);

            List<String> iptablesRules = Lists.newArrayList();

            if (isLocationFirewalldEnabled(machine)) {
                for (Integer port : inboundPorts) {
                    iptablesRules.add(IptablesCommands.addFirewalldRule(Chain.INPUT, Protocol.TCP, port, Policy.ACCEPT));
                 }
            } else {
                iptablesRules = createIptablesRulesForNetworkInterface(inboundPorts);
                iptablesRules.add(IptablesCommands.saveIptablesRules());
            }
            List<String> batch = Lists.newArrayList();

            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errStream = new ByteArrayOutputStream();
            Tasks.addTagDynamically(BrooklynTaskTags.tagForStreamSoft(BrooklynTaskTags.STREAM_STDOUT, outStream));
            Tasks.addTagDynamically(BrooklynTaskTags.tagForStreamSoft(BrooklynTaskTags.STREAM_STDERR, errStream));
            // Some entities, such as Riak (erlang based) have a huge range of ports, which leads to a script that
            // is too large to run (fails with a broken pipe). Batch the rules into batches of 50
            for (String rule : iptablesRules) {
                batch.add(rule);
                if (batch.size() == 50) {
                    machine.execCommands(ImmutableMap.of("out", outStream, "err", errStream), "Inserting iptables rules, 50 command batch", batch);
                    batch.clear();
                }
            }
            if (batch.size() > 0) {
                machine.execCommands(ImmutableMap.of("out", outStream, "err", errStream), "Inserting iptables rules", batch);
            }
            machine.execCommands(ImmutableMap.of("out", outStream, "err", errStream), "List iptables rules", ImmutableList.of(IptablesCommands.listIptablesRule()));
        }
    }

    protected void stopIptablesImpl(SshMachineLocation machine) {
        log.info("Stopping iptables for {} at {}", entity(), machine);

        List<String> cmds = ImmutableList.<String>of();
        if (isLocationFirewalldEnabled(machine)) {
            cmds = ImmutableList.of(IptablesCommands.firewalldServiceStop(), IptablesCommands.firewalldServiceStatus());
        } else {
            cmds = ImmutableList.of(IptablesCommands.iptablesServiceStop(), IptablesCommands.iptablesServiceStatus());
        }
        machine.execCommands("Stopping iptables", cmds);
    }
    
    private List<String> createIptablesRulesForNetworkInterface(Iterable<Integer> ports) {
        List<String> iptablesRules = Lists.newArrayList();
        for (Integer port : ports) {
           iptablesRules.add(IptablesCommands.insertIptablesRule(Chain.INPUT, Protocol.TCP, port, Policy.ACCEPT));
        }
        return iptablesRules;
     }

    public boolean isLocationFirewalldEnabled(SshMachineLocation location) {
        int result = location.execCommands("checking if firewalld is active", 
                ImmutableList.of(IptablesCommands.firewalldServiceIsActive()));
        if (result == 0) {
            return true;
        }
        
        return false;
    }
    
    protected String toTruncatedString(Iterable<?> vals, int maxShown) {
        StringBuilder result = new StringBuilder("[");
        int shown = 0;
        for (Object val : (vals == null ? ImmutableList.of() : vals)) {
            if (shown != 0) {
                result.append(", ");
            }
            if (shown < maxShown) {
                result.append(Strings.toString(val));
                shown++;
            } else {
                result.append("...");
                break;
            }
        }
        result.append("]");
        return result.toString();
    }
}
