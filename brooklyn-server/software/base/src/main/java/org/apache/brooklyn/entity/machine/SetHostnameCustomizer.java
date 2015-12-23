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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Arrays;

import org.apache.brooklyn.api.location.BasicMachineLocationCustomizer;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks.SshEffectorTaskFactory;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.util.core.text.TemplateProcessor;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

/**
 * Sets the hostname on an ssh'able machine. Currently only CentOS and RHEL are supported.
 * <p>
 * The customizer can be configured with a hard-coded hostname, or with a freemarker template
 * whose value (after substitutions) will be used for the hostname. 
 */
public class SetHostnameCustomizer extends BasicMachineLocationCustomizer {

    public static final Logger log = LoggerFactory.getLogger(SetHostnameCustomizer.class);
    
    public static final ConfigKey<String> FIXED_HOSTNAME = ConfigKeys.newStringConfigKey(
            "hostname.fixed",
            "The statically defined hostname to be set on the machine (if non-null)");
    
    public static final ConfigKey<String> FIXED_DOMAIN = ConfigKeys.newStringConfigKey(
            "domain.fixed",
            "The statically defined domain name to be set on the machine (if non-null)");
    
    // the  var??  tests if it exists, passing value to ?string(if_present,if_absent)
    // the ! provides a default value afterwards, which is never used, but is required for parsing
    // when the config key is not available;
    // thus the below prefers the first private address, then the first public address, and then
    // substitutes dots for dashes.
    public static final ConfigKey<String> HOSTNAME_TEMPLATE = ConfigKeys.newStringConfigKey(
            "hostname.templated",
            "The hostname template, to be resolved and then set on the machine (if non-null). "
                    +"Assumed to be in free-marker format.",
            "ip-${("
                    + "(location.privateAddresses[0]??)?string("
                            + "location.privateAddresses[0]!'X', "
                            + "(location.publicAddresses[0]??)?string("
                                    + "location.publicAddresses[0]!'X', "
                                    + "\"none\"))"
                    + ")"
                    + "?replace(\".\",\"-\")}"
                    + "-${location.id}");

    public static final ConfigKey<String> LOCAL_HOSTNAME = ConfigKeys.newStringConfigKey(
            "hostname.local.hostname",
            "Host name, as known on the local box. Config is set on the location.");
    
    public static final ConfigKey<String> LOCAL_IP = ConfigKeys.newStringConfigKey(
            "hostname.local.address",
            "Host address, as known on the local box. Config is set on the location.");
    
    public static final ConfigKey<Predicate<? super MachineLocation>> MACHINE_FILTER = ConfigKeys.newConfigKey(
            new TypeToken<Predicate<? super MachineLocation>>() {},
            "machineFilter",
            "A filter to say which machines this should be applied to",
            Predicates.instanceOf(SshMachineLocation.class));
    
    private final ConfigBag config;

    public SetHostnameCustomizer(ConfigBag config) {
        // TODO Any checks that they've given us sufficient configuration?
        this.config = config;
    }
    
    @Override
    public void customize(MachineLocation machine) {
        if (config.get(MACHINE_FILTER).apply(machine)) {
            log.info("SetHostnameCustomizer setting hostname on "+machine);
        } else {
            log.info("SetHostnameCustomizer ignoring non-ssh machine "+machine);
            return;
        }

        try {
            String localHostname = setLocalHostname((SshMachineLocation) machine);
            machine.config().set(LOCAL_HOSTNAME, localHostname);
    
            String localIp = execHostnameMinusI((SshMachineLocation) machine);
            machine.config().set(LOCAL_IP, localIp);
        } catch (Exception e) {
            log.info("SetHostnameCustomizer failed to set hostname on "+machine+" (rethrowing)", e);
            throw e;
        }
    }

    protected String generateHostname(SshMachineLocation machine) {
        String hostnameTemplate = config.get(HOSTNAME_TEMPLATE);
        if (Strings.isNonBlank(hostnameTemplate)) {
            return TemplateProcessor.processTemplateContents(hostnameTemplate, machine, ImmutableMap.<String, Object>of());
        } else {
            return null;
        }
    }

    /**
     * Sets the machine's hostname to the value controlled by fixed_hostname and hostname_template. 
     * If these are blank (and fixed_domain is blank), then just return the current hostname of 
     * the machine.
     */
    public String setLocalHostname(SshMachineLocation machine) {
        String hostFixed = config.get(FIXED_HOSTNAME);
        String domainFixed = config.get(FIXED_DOMAIN);
        String hostnameTemplate = config.get(HOSTNAME_TEMPLATE);
        
        String hostname;
        if (Strings.isNonBlank(hostFixed)) {
            hostname = hostFixed;
        } else {
            if (Strings.isNonBlank(hostnameTemplate)) {
                hostname = generateHostname(machine);
            } else {
                hostname = execHostname(machine);
                if (Strings.isBlank(domainFixed)) {
                    return hostname;
                }
            }
        }
        
        return setLocalHostname(machine, hostname, domainFixed);
    }
    
    /**
     * Sets the machine's hostname to the given value, ensuring /etc/hosts and /etc/sysconfig/network are both 
     * correctly updated.
     */
    public String setLocalHostname(SshMachineLocation machine, String hostName, String domainFixed) {
        log.info("Setting local hostname of " + machine + " to " + hostName 
                + (Strings.isNonBlank(domainFixed) ? ", " + domainFixed : ""));
        
        boolean hasDomain = Strings.isNonBlank(domainFixed);
        String fqdn = hasDomain ? hostName+"."+domainFixed : hostName;
        
        exec(machine, true, 
                BashCommands.sudo(String.format("sed -i.bak -e '1i127.0.0.1 %s %s' -e '/^127.0.0.1/d' /etc/hosts", fqdn, hostName)),
                BashCommands.sudo(String.format("sed -i.bak -e 's/^HOSTNAME=.*$/HOSTNAME=%s/' /etc/sysconfig/network", fqdn)),
                BashCommands.sudo(String.format("hostname %s", fqdn)));

        return hostName;
    }
    
    protected void registerEtcHosts(SshMachineLocation machine, String ip, Iterable<String> hostnames) {
        log.info("Updating /etc/hosts of "+machine+": adding "+ip+" = "+hostnames);
        
        checkArgument(Strings.isNonBlank(ip) && Networking.isValidIp4(ip), "invalid IPv4 address %s", ip);
        if (Strings.isBlank(ip) || Iterables.isEmpty(hostnames)) return;
        String line = ip+" "+Joiner.on(" ").join(hostnames);
        exec(machine, true, "echo " + line + " >> /etc/hosts");
    }
    
    protected String execHostname(SshMachineLocation machine) {
        if (log.isDebugEnabled()) log.debug("Retrieve `hostname` via ssh for {}", machine);
        
        ProcessTaskWrapper<Integer> cmd = exec(machine, false, "echo hostname=`hostname`");
//        ProcessTaskWrapper<Integer> cmd = DynamicTasks.queue(SshEffectorTasks.ssh(machine, "echo hostname=`hostname`")
//                .summary("getHostname"))
//                .block();

        for (String line : cmd.getStdout().split("\n")) {
            if (line.contains("hostname=") && !line.contains("`hostname`")) {
                return line.substring(line.indexOf("hostname=") + "hostname=".length()).trim();
            }
        }
        log.info("No hostname found for {} (got {}; {})", new Object[] {machine, cmd.getStdout(), cmd.getStderr()});
        return null;
    }

    protected String execHostnameMinusI(SshMachineLocation machine) {
        if (log.isDebugEnabled()) log.debug("Retrieve `hostname -I` via ssh for {}", machine);
        
        ProcessTaskWrapper<Integer> cmd = exec(machine, false, "echo localip=`hostname -I`");

        for (String line : cmd.getStdout().split("\n")) {
            if (line.contains("localip=") && !line.contains("`hostname -I`")) {
                return line.substring(line.indexOf("localip=") + "localip=".length()).trim();
            }
        }
        log.info("No local ip found for {} (got {}; {})", new Object[] {machine, cmd.getStdout(), cmd.getStderr()});
        return null;
    }
    
    protected ProcessTaskWrapper<Integer> exec(SshMachineLocation machine, boolean asRoot, String... cmds) {
        SshEffectorTaskFactory<Integer> taskFactory = SshEffectorTasks.ssh(machine, cmds);
        if (asRoot) taskFactory.runAsRoot();
        ProcessTaskWrapper<Integer> result = DynamicTasks.queue(taskFactory).block();
        if (result.get() != 0) {
            throw new IllegalStateException("SetHostnameCustomizer got exit code "+result.get()+" executing on machine "+machine
                    +"; cmds="+Arrays.asList(cmds)+"; stdout="+result.getStdout()+"; stderr="+result.getStderr());
        }
        return result;
    }
}
