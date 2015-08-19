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
package org.apache.brooklyn.core.location;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.location.HardwareDetails;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.MachineDetails;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.location.OsDetails;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.api.location.PortSupplier;
import org.apache.brooklyn.core.location.AbstractLocation;
import org.apache.brooklyn.core.location.BasicHardwareDetails;
import org.apache.brooklyn.core.location.BasicMachineDetails;
import org.apache.brooklyn.core.location.BasicOsDetails;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.net.Networking;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;


/** Location for use in dev/test, defining custom start/stop support, and/or tweaking the ports which are permitted to be available
 * (using setPermittedPorts(Iterable))
 */
public class SimulatedLocation extends AbstractLocation implements MachineProvisioningLocation<MachineLocation>, MachineLocation, PortSupplier {

    private static final long serialVersionUID = 1L;
    
    private static final InetAddress address;
    static {
        address = Networking.getLocalHost();
    }

    Iterable<Integer> permittedPorts = PortRanges.fromString("1+");
    Set<Integer> usedPorts = Sets.newLinkedHashSet();

    public SimulatedLocation() {
        this(MutableMap.<String,Object>of());
    }
    public SimulatedLocation(Map<String,? extends Object> flags) {
        super(flags);
    }
    
    @Override
    public SimulatedLocation newSubLocation(Map<?,?> newFlags) {
        // TODO shouldn't have to copy config bag as it should be inherited (but currently it is not used inherited everywhere; just most places)
        return getManagementContext().getLocationManager().createLocation(LocationSpec.create(getClass())
                .parent(this)
                .configure(config().getLocalBag().getAllConfig())  // FIXME Should this just be inherited?
                .configure(newFlags));
    }

    public MachineLocation obtain(Map<?,?> flags) {
        return this;
    }

    public void release(MachineLocation machine) {
    }

    public Map<String,Object> getProvisioningFlags(Collection<String> tags) {
        return MutableMap.<String,Object>of();
    }
    
    public InetAddress getAddress() {
        return address;
    }

    @Override
    public String getHostname() {
        String hostname = address.getHostName();
        return (hostname == null || hostname.equals(address.getHostAddress())) ? null : hostname;
    }
    
    @Override
    public Set<String> getPublicAddresses() {
        return ImmutableSet.of(address.getHostAddress());
    }
    
    @Override
    public Set<String> getPrivateAddresses() {
        return ImmutableSet.of();
    }

    public synchronized boolean obtainSpecificPort(int portNumber) {
        if (!Iterables.contains(permittedPorts, portNumber)) return false;
        if (usedPorts.contains(portNumber)) return false;
        usedPorts.add(portNumber);
        return true;
    }

    public synchronized int obtainPort(PortRange range) {
        for (int p: range)
            if (obtainSpecificPort(p)) return p;
        return -1;
    }

    public synchronized void releasePort(int portNumber) {
        usedPorts.remove(portNumber);
    }
    
    public synchronized void setPermittedPorts(Iterable<Integer> ports) {
        permittedPorts  = ports;
    }

    @Override
    public OsDetails getOsDetails() {
        return getMachineDetails().getOsDetails();
    }

    @Override
    public MachineDetails getMachineDetails() {
        HardwareDetails hardwareDetails = new BasicHardwareDetails(null, null);
        OsDetails osDetails = BasicOsDetails.Factory.ANONYMOUS_LINUX;
        return new BasicMachineDetails(hardwareDetails, osDetails);
    }
}
