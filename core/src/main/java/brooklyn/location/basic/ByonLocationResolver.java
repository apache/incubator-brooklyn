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
package brooklyn.location.basic;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.util.JavaGroovyEquivalents;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.WildcardGlobs;
import brooklyn.util.text.WildcardGlobs.PhraseTreatment;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

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
    protected ConfigBag extractConfig(Map<?,?> locationFlags, String spec, brooklyn.location.LocationRegistry registry) {
        ConfigBag config = super.extractConfig(locationFlags, spec, registry);

        Object hosts = config.getStringKey("hosts");
        config.remove("hosts");
        String user = (String)config.getStringKey("user");
        
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
        
        List<SshMachineLocation> machines = Lists.newArrayList();
        for (String host : hostAddresses) {
            SshMachineLocation machine;
            String userHere = user;
            String hostHere = host;
            if (host.contains("@")) {
                userHere = host.substring(0, host.indexOf("@"));
                hostHere = host.substring(host.indexOf("@")+1);
            }
            try {
                InetAddress.getByName(hostHere.trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid host '"+hostHere+"' specified in '"+spec+"': "+e);
            }
            if (JavaGroovyEquivalents.groovyTruth(userHere)) {
                machine = managementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                        .configure("user", userHere.trim())
                        .configure("address", hostHere.trim()));    
            } else {
                machine = managementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                        .configure("address", hostHere.trim()));    
            }
            machines.add(machine);
        }
        
        config.putStringKey("machines", machines);

        return config;
    }
}
