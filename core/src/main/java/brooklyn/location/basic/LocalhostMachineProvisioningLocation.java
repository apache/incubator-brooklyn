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

import static brooklyn.util.GroovyJavaMethods.elvis;
import static brooklyn.util.GroovyJavaMethods.truth;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.AddressableLocation;
import brooklyn.location.LocationSpec;
import brooklyn.location.OsDetails;
import brooklyn.location.PortRange;
import brooklyn.location.geo.HostGeoInfo;
import brooklyn.util.BrooklynNetworkUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.internal.ssh.process.ProcessTool;
import brooklyn.util.mutex.MutexSupport;
import brooklyn.util.mutex.WithMutexes;
import brooklyn.util.net.Networking;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * An implementation of {@link brooklyn.location.MachineProvisioningLocation} that can provision a {@link SshMachineLocation} for the
 * local host.
 *
 * By default you can only obtain a single SshMachineLocation for the localhost. Optionally, you can "overload"
 * and choose to allow localhost to be provisioned multiple times, which may be useful in some testing scenarios.
 */
public class LocalhostMachineProvisioningLocation extends FixedListMachineProvisioningLocation<SshMachineLocation> implements AddressableLocation {

    public static final Logger LOG = LoggerFactory.getLogger(LocalhostMachineProvisioningLocation.class);
    
    public static final ConfigKey<Boolean> SKIP_ON_BOX_BASE_DIR_RESOLUTION = ConfigKeys.newConfigKeyWithDefault(
            BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, 
            true);
    
    @SetFromFlag("count")
    int initialCount;

    @SetFromFlag
    Boolean canProvisionMore;
    
    @SetFromFlag
    InetAddress address;

    private static Set<Integer> portsInUse = Sets.newLinkedHashSet();

    private static HostGeoInfo cachedHostGeoInfo;
    
    @VisibleForTesting
    public static void clearStaticData() {
        portsInUse.clear();
        cachedHostGeoInfo = null;
    }
        
    /**
     * Construct a new instance.
     *
     * The constructor recognises the following properties:
     * <ul>
     * <li>count - number of localhost machines to make available
     * </ul>
     */
    public LocalhostMachineProvisioningLocation() {
        this(Maps.newLinkedHashMap());
    }
    /**
     * @param properties the properties of the new instance.
     * @deprecated since 0.6
     * @see #LocalhostMachineProvisioningLocation()
     */
    public LocalhostMachineProvisioningLocation(Map properties) {
        super(properties);
    }
    public LocalhostMachineProvisioningLocation(String name) {
        this(name, 0);
    }
    public LocalhostMachineProvisioningLocation(String name, int count) {
        this(MutableMap.of("name", name, "count", count));
    }
    
    public static LocationSpec<LocalhostMachineProvisioningLocation> spec() {
        return LocationSpec.create(LocalhostMachineProvisioningLocation.class);
    }
    
    public void configure(Map flags) {
        super.configure(flags);
        
        if (!truth(getDisplayName())) { setDisplayName("localhost"); }
        if (!truth(address)) address = getLocalhostInetAddress();
        // TODO should try to confirm this machine is accessible on the given address ... but there's no 
        // immediate convenience in java so early-trapping of that particular error is deferred
        
        if (canProvisionMore==null) {
            if (initialCount>0) canProvisionMore = false;
            else canProvisionMore = true;
        }
        if (getHostGeoInfo()==null) {
            if (cachedHostGeoInfo==null)
                cachedHostGeoInfo = HostGeoInfo.fromLocation(this);
            setHostGeoInfo(cachedHostGeoInfo);
        }
        if (initialCount > getMachines().size()) {
            provisionMore(initialCount - getMachines().size());
        }
        
        if (getConfig(BrooklynConfigKeys.ONBOX_BASE_DIR)==null && (getManagementContext()==null || getManagementContext().getConfig().getConfig(BrooklynConfigKeys.ONBOX_BASE_DIR)==null)) {
            setConfig(BrooklynConfigKeys.ONBOX_BASE_DIR, "/tmp/brooklyn-"+Os.user());
        }
    }
    
    public static InetAddress getLocalhostInetAddress() {
        return BrooklynNetworkUtils.getLocalhostInetAddress();
    }

    @Override
    public InetAddress getAddress() {
        return address;
    }
    
    @Override
    public boolean canProvisionMore() {
        return canProvisionMore;
    }
    
    @Override
    protected void provisionMore(int size, Map<?,?> flags) {
        for (int i=0; i<size; i++) {
            Map<Object,Object> flags2 = MutableMap.<Object,Object>builder()
                    .putAll(flags)
                    .put("address", elvis(address, Networking.getLocalHost()))
                    .put("mutexSupport", LocalhostMachine.mutexSupport)
                    .build();
            
            // copy inherited keys for ssh; 
            // shouldn't be necessary but not sure that all contexts traverse the hierarchy
            // NOTE: changed Nov 2013 to copy only those ssh config keys actually set, rather than all of them
            // TODO should take the plunge and try removing this altogether!
            // (or alternatively switch to copying all ancestor keys)
            for (HasConfigKey<?> k: SshMachineLocation.ALL_SSH_CONFIG_KEYS) {
                if (hasConfig(k.getConfigKey(), true))
                    flags2.put(k, getConfig(k));
            }
            
            if (isManaged()) {
                addChild(LocationSpec.create(LocalhostMachine.class).configure(flags2));
            } else {
                addChild(new LocalhostMachine(flags2)); // TODO legacy way
            }
       }
    }

    public static synchronized boolean obtainSpecificPort(InetAddress localAddress, int portNumber) {
        if (portsInUse.contains(portNumber)) {
            return false;
        } else {
            //see if it is available?
            if (!checkPortAvailable(localAddress, portNumber)) {
                return false;
            }
            portsInUse.add(portNumber);
            return true;
        }
    }
    /** checks the actual availability of the port on localhost, ie by binding to it; cf {@link Networking#isPortAvailable(int)} */
    public static boolean checkPortAvailable(InetAddress localAddress, int portNumber) {
        if (portNumber<1024) {
            if (LOG.isDebugEnabled()) LOG.debug("Skipping system availability check for privileged localhost port "+portNumber);
            return true;
        }
        return Networking.isPortAvailable(portNumber);
    }
    public static int obtainPort(PortRange range) {
        return obtainPort(getLocalhostInetAddress(), range);
    }
    public static int obtainPort(InetAddress localAddress, PortRange range) {
        for (int p: range)
            if (obtainSpecificPort(localAddress, p)) return p;
        if (LOG.isDebugEnabled()) LOG.debug("unable to find port in {} on {}; returning -1", range, localAddress);
        return -1;
    }

    public static synchronized void releasePort(InetAddress localAddress, int portNumber) {
        portsInUse.remove((Object) portNumber);
    }

    public void release(SshMachineLocation machine) {
        LocalhostMachine localMachine = (LocalhostMachine) machine;
        Set<Integer> portsObtained = Sets.newLinkedHashSet();
        synchronized (localMachine.portsObtained) {
            portsObtained.addAll(localMachine.portsObtained);
        }
        
        super.release(machine);
        
        for (int p: portsObtained)
            releasePort(null, p);
    }
    
    public static class LocalhostMachine extends SshMachineLocation {
        // declaring this here (as well as on LocalhostMachineProvisioningLocation) because:
        //  1. machine.getConfig(key) will not inherit default value of machine.getParent()'s key
        //  2. things might instantiate a `LocalhostMachine` without going through LocalhostMachineProvisioningLocation
        //     so not sufficient for LocalhostMachineProvisioningLocation to just push its config value into
        //     the LocalhostMachine instance.
        public static final ConfigKey<Boolean> SKIP_ON_BOX_BASE_DIR_RESOLUTION = ConfigKeys.newConfigKeyWithDefault(
                BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, 
                true);

        private static final WithMutexes mutexSupport = new MutexSupport();
        
        private final Set<Integer> portsObtained = Sets.newLinkedHashSet();

        public LocalhostMachine() {
            super();
        }
        /** @deprecated since 0.6.0 use no-arg constructor (and spec) then configure */
        public LocalhostMachine(Map properties) {
            super(MutableMap.builder().putAll(properties).put("mutexSupport", mutexSupport).build());
        }
        public boolean obtainSpecificPort(int portNumber) {
            if (!isSudoAllowed() && portNumber <= 1024)
                return false;
            return LocalhostMachineProvisioningLocation.obtainSpecificPort(getAddress(), portNumber);
        }
        public int obtainPort(PortRange range) {
            int r = LocalhostMachineProvisioningLocation.obtainPort(getAddress(), range);
            synchronized (portsObtained) {
                if (r>0) portsObtained.add(r);
            }
            LOG.debug("localhost.obtainPort("+range+"), returning "+r);
            return r;
        }
        @Override
        public void releasePort(int portNumber) {
            synchronized (portsObtained) {
                portsObtained.remove((Object)portNumber);
            }
            LocalhostMachineProvisioningLocation.releasePort(getAddress(), portNumber);
        }
        
        @Override
        public OsDetails getOsDetails() {
            return BasicOsDetails.Factory.newLocalhostInstance();
        }
        
        @Override
        public void configure(Map properties) {
            if (address==null || !properties.containsKey("address"))
                address = Networking.getLocalHost();
            super.configure(properties);
        }
    }

    private static class SudoChecker {
        static volatile long lastSudoCheckTime = -1;
        static boolean lastSudoResult = false;
        public static boolean isSudoAllowed() {
            if (Time.hasElapsedSince(lastSudoCheckTime, Duration.FIVE_MINUTES))
                checkIfNeeded();                    
            return lastSudoResult;
        }
        private static synchronized void checkIfNeeded() {
            if (Time.hasElapsedSince(lastSudoCheckTime, Duration.FIVE_MINUTES)) {
                try {
                    lastSudoResult = new ProcessTool().execCommands(MutableMap.<String,Object>of(), Arrays.asList(
                            BashCommands.sudo("date"))) == 0;
                } catch (Exception e) {
                    lastSudoResult = false;
                    LOG.debug("Error checking sudo at localhost: "+e, e);
                }
                lastSudoCheckTime = System.currentTimeMillis();
            }
        }
    }

    public static boolean isSudoAllowed() {
        return SudoChecker.isSudoAllowed();
    }

}
