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
package org.apache.brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkArgument;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationRegistry;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.management.internal.LocalLocationManager;
import org.apache.brooklyn.core.util.config.ConfigBag;
import org.apache.brooklyn.core.util.flags.TypeCoercions;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.net.UserAndHostAndPort;
import org.apache.brooklyn.util.text.WildcardGlobs;
import org.apache.brooklyn.util.text.WildcardGlobs.PhraseTreatment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.net.HostAndPort;

import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Sanitizer;

/**
 * Examples of valid specs:
 *   <ul>
 *     <li>byon:(hosts=myhost)
 *     <li>byon:(hosts=myhost,myhost2)
 *     <li>byon:(hosts="myhost, myhost2")
 *     <li>byon:(hosts=myhost,myhost2, name=abc)
 *     <li>byon:(hosts="myhost, myhost2", name="my location name")
 *   </ul>
 * 
 * @author aled
 */
@SuppressWarnings({"rawtypes"})
public class ByonLocationResolver extends AbstractLocationResolver {

    public static final Logger log = LoggerFactory.getLogger(ByonLocationResolver.class);
    
    public static final String BYON = "byon";

    public static final ConfigKey<String> OS_FAMILY = ConfigKeys.newStringConfigKey("osfamily", "OS Family of the machine, either windows or linux", "linux");

    public static final Map<String, Class<? extends MachineLocation>> OS_TO_MACHINE_LOCATION_TYPE = ImmutableMap.<String, Class<? extends MachineLocation>>of(
            "windows", WinRmMachineLocation.class,
            "linux", SshMachineLocation.class);

    @Override
    public String getPrefix() {
        return BYON;
    }

    @Override
    protected Class<? extends Location> getLocationType() {
        return FixedListMachineProvisioningLocation.class;
    }

    @Override
    protected SpecParser getSpecParser() {
        return new AbstractLocationResolver.SpecParser(getPrefix()).setExampleUsage("\"byon(hosts='addr1,addr2')\"");
    }

    @Override
    protected ConfigBag extractConfig(Map<?,?> locationFlags, String spec, LocationRegistry registry) {
        ConfigBag config = super.extractConfig(locationFlags, spec, registry);

        Object hosts = config.getStringKey("hosts");
        config.remove("hosts");
        String user = (String) config.getStringKey("user");
        Integer port = (Integer) TypeCoercions.coerce(config.getStringKey("port"), Integer.class);
        Class<? extends MachineLocation> locationClass = OS_TO_MACHINE_LOCATION_TYPE.get(config.get(OS_FAMILY));

        MutableMap<String, Object> defaultProps = MutableMap.of();
        defaultProps.addIfNotNull("user", user);
        defaultProps.addIfNotNull("port", port);

        List<String> hostAddresses;
        
        if (hosts instanceof String) {
            if (((String) hosts).isEmpty()) {
                hostAddresses = ImmutableList.of();
            } else {
                hostAddresses = WildcardGlobs.getGlobsAfterBraceExpansion("{"+hosts+"}",
                        true /* numeric */, /* no quote support though */ PhraseTreatment.NOT_A_SPECIAL_CHAR, PhraseTreatment.NOT_A_SPECIAL_CHAR);
            }
        } else if (hosts instanceof Iterable) {
            hostAddresses = ImmutableList.copyOf((Iterable<String>)hosts);
        } else {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; at least one host must be defined");
        }
        if (hostAddresses.isEmpty()) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; at least one host must be defined");
        }
        
        List<MachineLocation> machines = Lists.newArrayList();
        for (Object host : hostAddresses) {
            LocationSpec<? extends MachineLocation> machineSpec;
            if (host instanceof String) {
                machineSpec = parseMachine((String)host, locationClass, defaultProps, spec);
            } else if (host instanceof Map) {
                machineSpec = parseMachine((Map<String, ?>)host, locationClass, defaultProps, spec);
            } else {
                throw new IllegalArgumentException("Expected machine to be String or Map, but was "+host.getClass().getName()+" ("+host+")");
            }
            machineSpec.configureIfNotNull(LocalLocationManager.CREATE_UNMANAGED, config.get(LocalLocationManager.CREATE_UNMANAGED));
            MachineLocation machine = managementContext.getLocationManager().createLocation(machineSpec);
            machines.add(machine);
        }
        
        config.putStringKey("machines", machines);

        return config;
    }
    
    protected LocationSpec<? extends MachineLocation> parseMachine(Map<String, ?> vals, Class<? extends MachineLocation> locationClass, Map<String, ?> defaults, String specForErrMsg) {
        Map<String, Object> valSanitized = Sanitizer.sanitize(vals);
        Map<String, Object> machineConfig = MutableMap.copyOf(vals);
        
        String osfamily = (String) machineConfig.remove(OS_FAMILY.getName());
        String ssh = (String) machineConfig.remove("ssh");
        String winrm = (String) machineConfig.remove("winrm");
        Map<Integer, String> tcpPortMappings = (Map<Integer, String>) machineConfig.get("tcpPortMappings");
        
        checkArgument(ssh != null ^ winrm != null, "Must specify exactly one of 'ssh' or 'winrm' for machine: %s", valSanitized);
        
        UserAndHostAndPort userAndHostAndPort;
        String host;
        int port;
        if (ssh != null) {
            userAndHostAndPort = parseUserAndHostAndPort((String)ssh, 22);
        } else {
            userAndHostAndPort = parseUserAndHostAndPort((String)winrm, 5985);
        }
        
        // If there is a tcpPortMapping defined for the connection-port, then use that for ssh/winrm machine
        port = userAndHostAndPort.getHostAndPort().getPort();
        if (tcpPortMappings != null && tcpPortMappings.containsKey(port)) {
            String override = tcpPortMappings.get(port);
            HostAndPort hostAndPortOverride = HostAndPort.fromString(override);
            if (!hostAndPortOverride.hasPort()) {
                throw new IllegalArgumentException("Invalid portMapping ('"+override+"') for port "+port+" in "+specForErrMsg);
            }
            port = hostAndPortOverride.getPort();
            host = hostAndPortOverride.getHostText().trim();
        } else {
            host = userAndHostAndPort.getHostAndPort().getHostText().trim();
        }
        
        machineConfig.put("address", host);
        try {
            InetAddress.getByName(host);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid host '"+host+"' specified in '"+specForErrMsg+"': "+e);
        }

        if (userAndHostAndPort.getUser() != null) {
            checkArgument(!vals.containsKey("user"), "Must not specify user twice for machine: %s", valSanitized);
            machineConfig.put("user", userAndHostAndPort.getUser());
        }
        if (userAndHostAndPort.getHostAndPort().hasPort()) {
            checkArgument(!vals.containsKey("port"), "Must not specify port twice for machine: %s", valSanitized);
            machineConfig.put("port", port);
        }
        for (Map.Entry<String, ?> entry : defaults.entrySet()) {
            if (!machineConfig.containsKey(entry.getKey())) {
                machineConfig.put(entry.getKey(), entry.getValue());
            }
        }
        
        Class<? extends MachineLocation> locationClassHere = locationClass;
        if (osfamily != null) {
            locationClassHere = OS_TO_MACHINE_LOCATION_TYPE.get(osfamily);
        }

        return LocationSpec.create(locationClassHere).configure(machineConfig);
    }

    protected LocationSpec<? extends MachineLocation> parseMachine(String val, Class<? extends MachineLocation> locationClass, Map<String, ?> defaults, String specForErrMsg) {
        Map<String, Object> machineConfig = Maps.newLinkedHashMap();
        
        UserAndHostAndPort userAndHostAndPort = parseUserAndHostAndPort(val);
        
        String host = userAndHostAndPort.getHostAndPort().getHostText().trim();
        machineConfig.put("address", host);
        try {
            InetAddress.getByName(host.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid host '"+host+"' specified in '"+specForErrMsg+"': "+e);
        }
        
        if (userAndHostAndPort.getUser() != null) {
            machineConfig.put("user", userAndHostAndPort.getUser());
        }
        if (userAndHostAndPort.getHostAndPort().hasPort()) {
            machineConfig.put("port", userAndHostAndPort.getHostAndPort().getPort());
        }
        for (Map.Entry<String, ?> entry : defaults.entrySet()) {
            if (!machineConfig.containsKey(entry.getKey())) {
                machineConfig.put(entry.getKey(), entry.getValue());
            }
        }

        return LocationSpec.create(locationClass).configure(machineConfig);
    }
    
    private UserAndHostAndPort parseUserAndHostAndPort(String val) {
        String userPart = null;
        String hostPart = val;
        if (val.contains("@")) {
            userPart = val.substring(0, val.indexOf("@"));
            hostPart = val.substring(val.indexOf("@")+1);
        }
        return UserAndHostAndPort.fromParts(userPart, HostAndPort.fromString(hostPart));
    }
    
    private UserAndHostAndPort parseUserAndHostAndPort(String val, int defaultPort) {
        UserAndHostAndPort result = parseUserAndHostAndPort(val);
        if (!result.getHostAndPort().hasPort()) {
            result = UserAndHostAndPort.fromParts(result.getUser(), result.getHostAndPort().getHostText(), defaultPort);
        }
        return result;
    }
}
