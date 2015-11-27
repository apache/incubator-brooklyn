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

import java.util.List;

import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.util.core.task.system.ProcessTaskFactory;
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
     * Returns a queued {@link Task} which stops iptables on the given machine.
     */
    public Task<Void> stopIptablesAsync(final SshMachineLocation machine) {
        return DynamicTasks.queue("stop iptables", new Runnable() {
            public void run() {
                stopIptablesImpl(machine);
            }
        });
    }

    protected void stopIptablesImpl(final SshMachineLocation machine) {

        log.info("Stopping iptables for {} at {}", entity(), machine);

        List<String> cmds = ImmutableList.<String>of();

        Task<Integer> checkFirewall = checkLocationFirewall(machine);

        if (checkFirewall.getUnchecked() == 0) {
            cmds = ImmutableList.of(IptablesCommands.firewalldServiceStop(), IptablesCommands.firewalldServiceStatus());
        } else {
            cmds = ImmutableList.of(IptablesCommands.iptablesServiceStop(), IptablesCommands.iptablesServiceStatus());
        }


        subTaskHelperAllowingNonZeroExitCode("execute stop iptables", machine, cmds.toArray(new String[cmds.size()]));
    }


    /**
     * See docs in {@link BashCommands#dontRequireTtyForSudo()}
     */
    public Task<Boolean> dontRequireTtyForSudoAsync(final SshMachineLocation machine) {
        return DynamicTasks.queue(SshTasks.dontRequireTtyForSudo(machine, true).newTask().asTask());
    }

    /**
     * Returns a queued {@link Task} which opens the given ports in iptables on the given machine.
     */
    public Task<Void> openIptablesAsync(final Iterable<Integer> inboundPorts, final SshMachineLocation machine) {
        return DynamicTasks.queue("open iptables "+toTruncatedString(inboundPorts, 6), new Runnable() {
            public void run() {
                openIptablesImpl(inboundPorts, machine);
            }
        });
    }

    protected void openIptablesImpl(Iterable<Integer> inboundPorts, SshMachineLocation machine) {
        if (inboundPorts == null || Iterables.isEmpty(inboundPorts)) {
            log.info("No ports to open in iptables (no inbound ports) for {} at {}", machine, this);
        } else {
            log.info("Opening ports in iptables for {} at {}", entity(), machine);

            List<String> iptablesRules = Lists.newArrayList();
            String iptablesInstallCommands = null;

            Task<Integer> checkFirewall = checkLocationFirewall(machine);

            if (checkFirewall.getUnchecked() == 0) {
                for (Integer port : inboundPorts) {
                    iptablesRules.add(IptablesCommands.addFirewalldRule(Chain.INPUT, Protocol.TCP, port, Policy.ACCEPT));
                 }
            } else {
                iptablesRules = createIptablesRulesForNetworkInterface(inboundPorts);
                iptablesInstallCommands = IptablesCommands.saveIptablesRules();
            }

            insertIptablesRules(iptablesRules, iptablesInstallCommands, machine);
            listIptablesRules(machine);
        }
    }

    /**
     * Returns a queued {@link Task} which checks if location firewall is enabled.
     */
    public Task<Integer> checkLocationFirewall(final SshMachineLocation machine) {
        return subTaskHelperAllowingNonZeroExitCode("check if firewall is active", machine, IptablesCommands.firewalldServiceIsActive());
    }

    /**
     * Returns a queued {@link Task} which inserts iptables rules.
     */
    private Task<Void> insertIptablesRules(final List<String> iptablesRules, final String installCommands, final SshMachineLocation machine) {
        return DynamicTasks.queue("insert rules", new Runnable() {
            public void run() {
                insertIptablesRulesImpl(iptablesRules, installCommands, machine);
            }
        });
    }

    private void insertIptablesRulesImpl(List<String> iptablesRules, String installCommands, SshMachineLocation machine) {

        // Some entities, such as Riak (erlang based) have a huge range of ports, which leads to a script that
        // is too large to run (fails with a broken pipe). Batch the rules into batches of 100
        List<List<String> > batches = Lists.partition(iptablesRules, 100);

        int batchNumber = 0;
        for (List<String> batch : batches) {
            batchNumber++;
            insertIptablesRulesOnCommandBatches(batch, machine, batchNumber);
        }
        if (installCommands != null) {
            serviceIptablesSave(installCommands, machine);
        }
    }

    /**
     * Returns a queued {@link Task} which inserts iptables rules on command batches.
     */
    private Task<Integer> insertIptablesRulesOnCommandBatches(final List<String> commandsBatch, final SshMachineLocation machine, int batchNumber) {
        return subTaskHelperRequiringZeroExitCode("commands batch " + batchNumber, machine, commandsBatch.toArray(new String[commandsBatch.size()]));
    }

    /**
     * Returns a queued {@link Task} which runs iptables save commands.
     */
    private Task<Integer> serviceIptablesSave(final String installCommands, final SshMachineLocation machine) {
        return subTaskHelperRequiringZeroExitCode("save", machine, installCommands);
    }

    /**
     * Returns a queued {@link Task} which lists the iptables rules.
     */
    private Task<Integer> listIptablesRules(final SshMachineLocation machine) {
        return subTaskHelperRequiringZeroExitCode("list rules", machine, IptablesCommands.listIptablesRule());
    }

    private Task<Integer> subTaskHelperRequiringZeroExitCode(String taskName, SshMachineLocation machine, String... comands) {
        ProcessTaskFactory<Integer> taskFactory = SshTasks.newSshExecTaskFactory(machine, comands)
                .summary(taskName)
                .requiringExitCodeZero();
        return DynamicTasks.queue(taskFactory).asTask();
    }

    private Task<Integer> subTaskHelperAllowingNonZeroExitCode(String taskName, SshMachineLocation machine, String... comands) {
        ProcessTaskFactory<Integer> taskFactory = SshTasks.newSshExecTaskFactory(machine, comands)
                .summary(taskName)
                .allowingNonZeroExitCode();
        return DynamicTasks.queue(taskFactory).asTask();
    }
    
    private List<String> createIptablesRulesForNetworkInterface(Iterable<Integer> ports) {
        List<String> iptablesRules = Lists.newArrayList();
        for (Integer port : ports) {
           iptablesRules.add(IptablesCommands.insertIptablesRule(Chain.INPUT, Protocol.TCP, port, Policy.ACCEPT));
        }
        return iptablesRules;
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
