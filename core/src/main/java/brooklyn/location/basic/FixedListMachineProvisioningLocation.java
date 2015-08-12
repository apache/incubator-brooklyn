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

import static brooklyn.util.GroovyJavaMethods.truth;

import java.io.Closeable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.management.LocationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineLocationCustomizer;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.util.collections.CollectionFunctionals;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.stream.Streams;
import brooklyn.util.text.WildcardGlobs;
import brooklyn.util.text.WildcardGlobs.PhraseTreatment;

/**
 * A provisioner of {@link MachineLocation}s which takes a list of machines it can connect to.
 * The collection of initial machines should be supplied in the 'machines' flag in the constructor,
 * for example a list of machines which can be SSH'd to. 
 * 
 * This can be extended to have a mechanism to make more machines to be available
 * (override provisionMore and canProvisionMore).
 */
public class FixedListMachineProvisioningLocation<T extends MachineLocation> extends AbstractLocation 
implements MachineProvisioningLocation<T>, Closeable {

    // TODO Synchronization looks very wrong for accessing machines/inUse 
    // e.g. removeChild doesn't synchronize when doing machines.remove(...),
    // and getMachines() returns the real sets risking 
    // ConcurrentModificationException in the caller if it iterates over them etc.
    
    private static final Logger log = LoggerFactory.getLogger(FixedListMachineProvisioningLocation.class);
    
    public static final ConfigKey<Function<Iterable<? extends MachineLocation>, MachineLocation>> MACHINE_CHOOSER =
            ConfigKeys.newConfigKey(
                    new TypeToken<Function<Iterable<? extends MachineLocation>, MachineLocation>>() {}, 
                    "byon.machineChooser",
                    "For choosing which of the possible machines is chosen and returned by obtain()",
                    CollectionFunctionals.<MachineLocation>firstElement());

    public static final ConfigKey<Collection<MachineLocationCustomizer>> MACHINE_LOCATION_CUSTOMIZERS = CloudLocationConfig.MACHINE_LOCATION_CUSTOMIZERS;
    
    private final Object lock = new Object();
    
    @SetFromFlag
    protected Set<T> machines;
    
    @SetFromFlag
    protected Set<T> inUse;

    @SetFromFlag
    protected Set<T> pendingRemoval;
    
    @SetFromFlag
    protected Map<T, Map<String, Object>> origConfigs;

    public FixedListMachineProvisioningLocation() {
        this(Maps.newLinkedHashMap());
    }
    public FixedListMachineProvisioningLocation(Map properties) {
        super(properties);

        if (isLegacyConstruction()) {
            init();
        }
    }

    @Override
    public void init() {
        super.init();
        
        Set<T> machinesCopy = MutableSet.of();
        for (T location: machines) {
            if (location==null) {
                log.warn(""+this+" initialized with null location, removing (may be due to rebind with reference to an unmanaged location)");
            } else {
                Location parent = location.getParent();
                if (parent == null) {
                    addChild(location);
                }
                machinesCopy.add(location);
            }
        }
        if (!machinesCopy.equals(machines)) {
            machines = machinesCopy;
        }
    }
    
    @Override
    public String toVerboseString() {
        return Objects.toStringHelper(this).omitNullValues()
                .add("id", getId()).add("name", getDisplayName())
                .add("machinesAvailable", getAvailable()).add("machinesInUse", getInUse())
                .toString();
    }

    @Override
    public AbstractLocation configure(Map<?,?> properties) {
        if (machines == null) machines = Sets.newLinkedHashSet();
        if (inUse == null) inUse = Sets.newLinkedHashSet();
        if (pendingRemoval == null) pendingRemoval = Sets.newLinkedHashSet();
        if (origConfigs == null) origConfigs = Maps.newLinkedHashMap();
        return super.configure(properties);
    }
    
    @SuppressWarnings("unchecked")
    public FixedListMachineProvisioningLocation<T> newSubLocation(Map<?,?> newFlags) {
        // TODO shouldn't have to copy config bag as it should be inherited (but currently it is not used inherited everywhere; just most places)
        return getManagementContext().getLocationManager().createLocation(LocationSpec.create(getClass())
                .parent(this)
                .configure(config().getLocalBag().getAllConfig())  // FIXME Should this just be inherited?
                .configure(newFlags));
    }

    @Override
    public void close() {
        for (T machine : machines) {
            if (machine instanceof Closeable) Streams.closeQuietly((Closeable)machine);
        }
    }
    
    public void addMachine(T machine) {
        synchronized (lock) {
            if (machines.contains(machine)) {
                throw new IllegalArgumentException("Cannot add "+machine+" to "+toString()+", because already contained");
            }
            
            Location existingParent = ((Location)machine).getParent();
            if (existingParent == null) {
                addChild(machine);
            }
            
            machines.add(machine);
        }
    }
    
    public void removeMachine(T machine) {
        synchronized (lock) {
            if (inUse.contains(machine)) {
                pendingRemoval.add(machine);
            } else {
                machines.remove(machine);
                pendingRemoval.remove(machine);
                if (this.equals(machine.getParent())) {
                    removeChild((Location)machine);
                }
            }
        }
    }
    
    protected Set<T> getMachines() {
        return machines;
    }
    
    public Set<T> getAvailable() {
        Set<T> a = Sets.newLinkedHashSet(machines);
        a.removeAll(inUse);
        return a;
    }   
     
    public Set<T> getInUse() {
        return Sets.newLinkedHashSet(inUse);
    }   
     
    public Set<T> getAllMachines() {
        return ImmutableSet.copyOf(machines);
    }   
     
    @Override
    public void addChild(Location child) {
        super.addChild(child);
        machines.add((T)child);
    }

    @Override
    public boolean removeChild(Location child) {
        if (inUse.contains(child)) {
            throw new IllegalStateException("Child location "+child+" is in use; cannot remove from "+this);
        }
        machines.remove(child);
        return super.removeChild(child);
    }

    protected boolean canProvisionMore() {
        return false;
    }
    
    protected void provisionMore(int size) {
        provisionMore(size, ImmutableMap.of());
    }

    protected void provisionMore(int size, Map<?,?> flags) {
        throw new IllegalStateException("more not permitted");
    }

    public T obtain() throws NoMachinesAvailableException {
        return obtain(Maps.<String,Object>newLinkedHashMap());
    }
    
    @Override
    public T obtain(Map<?,?> flags) throws NoMachinesAvailableException {
        T machine;
        T desiredMachine = (T) flags.get("desiredMachine");
        ConfigBag allflags = ConfigBag.newInstanceExtending(config().getBag()).putAll(flags);
        Function<Iterable<? extends MachineLocation>, MachineLocation> chooser = allflags.get(MACHINE_CHOOSER);
        
        synchronized (lock) {
            Set<T> a = getAvailable();
            if (a.isEmpty()) {
                if (canProvisionMore()) {
                    provisionMore(1, allflags.getAllConfig());
                    a = getAvailable();
                }
                if (a.isEmpty())
                    throw new NoMachinesAvailableException("No machines available in "+toString());
            }
            if (desiredMachine != null) {
                if (a.contains(desiredMachine)) {
                    machine = desiredMachine;
                } else {
                    throw new IllegalStateException("Desired machine "+desiredMachine+" not available in "+toString()+"; "+
                            (inUse.contains(desiredMachine) ? "machine in use" : "machine unknown"));
                }
            } else {
                machine = (T) chooser.apply(a);
                if (!a.contains(machine)) {
                    throw new IllegalStateException("Machine chooser attempted to choose '"+machine+"' from outside the available set, in "+this);
                }
            }
            inUse.add(machine);
            updateMachineConfig(machine, flags);
        }
        
        for (MachineLocationCustomizer customizer : getMachineCustomizers(allflags)) {
            customizer.customize(machine);
        }
        
        return machine;
    }

    @Override
    public void release(T machine) {
        ConfigBag machineConfig = ((ConfigurationSupportInternal)machine.config()).getBag();
        for (MachineLocationCustomizer customizer : getMachineCustomizers(machineConfig)) {
            customizer.preRelease(machine);
        }

        synchronized (lock) {
            if (inUse.contains(machine) == false)
                throw new IllegalStateException("Request to release machine "+machine+", but this machine is not currently allocated");
            restoreMachineConfig(machine);
            inUse.remove(machine);
            
            if (pendingRemoval.contains(machine)) {
                removeMachine(machine);
            }
        }
    }

    @Override
    public Map<String,Object> getProvisioningFlags(Collection<String> tags) {
        return Maps.<String,Object>newLinkedHashMap();
    }
    
    protected void updateMachineConfig(T machine, Map<?, ?> flags) {
        if (origConfigs == null) {
            // For backwards compatibility, where peristed state did not have this.
            origConfigs = Maps.newLinkedHashMap();
        }
        Map<String, Object> strFlags = ConfigBag.newInstance(flags).getAllConfig();
        Map<String, Object> origConfig = ((ConfigurationSupportInternal)machine.config()).getLocalBag().getAllConfig();
        origConfigs.put(machine, origConfig);
        requestPersist();
        
        ((ConfigurationSupportInternal)machine.config()).addToLocalBag(strFlags);
    }
    
    protected void restoreMachineConfig(MachineLocation machine) {
        if (origConfigs == null) {
            // For backwards compatibility, where peristed state did not have this.
            origConfigs = Maps.newLinkedHashMap();
        }
        Map<String, Object> origConfig = origConfigs.remove(machine);
        if (origConfig == null) return;
        requestPersist();
        
        Set<String> currentKeys = ((ConfigurationSupportInternal)machine.config()).getLocalBag().getAllConfig().keySet();
        Set<String> newKeys = Sets.difference(currentKeys, origConfig.entrySet());
        for (String key : newKeys) {
            ((ConfigurationSupportInternal)machine.config()).removeFromLocalBag(key);
        }
        ((ConfigurationSupportInternal)machine.config()).addToLocalBag(origConfig);
    }
    
    @SuppressWarnings("unchecked")
    private <K> K getConfigPreferringOverridden(ConfigKey<K> key, Map<?,?> overrides) {
        K result = (K) overrides.get(key);
        if (result == null) result = (K) overrides.get(key.getName());
        if (result == null) result = getConfig(key);
        return result;
    }

    protected Collection<MachineLocationCustomizer> getMachineCustomizers(ConfigBag setup) {
        Collection<MachineLocationCustomizer> customizers = setup.get(MACHINE_LOCATION_CUSTOMIZERS);
        return (customizers == null ? ImmutableList.<MachineLocationCustomizer>of() : customizers);
    }

    /**
     * Facilitates fluent/programmatic style for constructing a fixed pool of machines.
     * <pre>
     * {@code
     *   new FixedListMachineProvisioningLocation.Builder()
     *           .user("alex")
     *           .keyFile("/Users/alex/.ssh/id_rsa")
     *           .addAddress("10.0.0.1")
     *           .addAddress("10.0.0.2")
     *           .addAddress("10.0.0.3")
     *           .addAddressMultipleTimes("me@127.0.0.1", 5)
     *           .build();
     * }
     * </pre>
     */
    public static class Builder {
        LocationManager lm;
        String user;
        String privateKeyPassphrase;
        String privateKeyFile;
        String privateKeyData;
        File localTempDir;
        List machines = Lists.newArrayList();

        public Builder(LocationManager lm) {
            this.lm = lm;
        }
        public Builder user(String user) {
            this.user = user;
            return this;
        }
        public Builder keyPassphrase(String keyPassphrase) {
            this.privateKeyPassphrase = keyPassphrase;
            return this; 
        }
        public Builder keyFile(String keyFile) {
            this.privateKeyFile = keyFile;
            return this; 
        }
        public Builder keyData(String keyData) {
            this.privateKeyData = keyData;
            return this;
        }
        public Builder localTempDir(File val) {
            this.localTempDir = val;
            return this;
        }
        /** adds the locations; user and keyfile set in the builder are _not_ applied to the machine
         * (use add(String address) for that)
         */
        public Builder add(SshMachineLocation location) {
            machines.add(location);
            return this;
        }
        public Builder addAddress(String address) {
            return addAddresses(address);
        }
        public Builder addAddressMultipleTimes(String address, int n) {
            for (int i=0; i<n; i++)
                addAddresses(address);
            return this;
        }
        public Builder addAddresses(String address1, String ...others) {
            List<String> addrs = new ArrayList<String>();
            addrs.addAll(WildcardGlobs.getGlobsAfterBraceExpansion("{"+address1+"}",
                    true /* numeric */, /* no quote support though */ PhraseTreatment.NOT_A_SPECIAL_CHAR, PhraseTreatment.NOT_A_SPECIAL_CHAR));
            for (String address: others) 
                addrs.addAll(WildcardGlobs.getGlobsAfterBraceExpansion("{"+address+"}",
                        true /* numeric */, /* no quote support though */ PhraseTreatment.NOT_A_SPECIAL_CHAR, PhraseTreatment.NOT_A_SPECIAL_CHAR));
            for (String addr: addrs)
                add(createMachine(addr)); 
            return this;
        }
        protected SshMachineLocation createMachine(String addr) {
            if (lm==null)
                return new SshMachineLocation(makeConfig(addr));
            else
                return lm.createLocation(makeConfig(addr), SshMachineLocation.class);
        }
        private Map makeConfig(String address) {
            String user = this.user;
            if (address.contains("@")) {
                user = address.substring(0, address.indexOf("@"));
                address = address.substring(address.indexOf("@")+1);
            }
            Map config = MutableMap.of("address", address);
            if (truth(user)) {
                config.put("user", user);
                config.put("sshconfig.user", user);
            }
            if (truth(privateKeyPassphrase)) config.put("sshconfig.privateKeyPassphrase", privateKeyPassphrase);
            if (truth(privateKeyFile)) config.put("sshconfig.privateKeyFile", privateKeyFile);
            if (truth(privateKeyData)) config.put("sshconfig.privateKey", privateKeyData);
            if (truth(localTempDir)) config.put("localTempDir", localTempDir);
            return config;
        }
        @SuppressWarnings("unchecked")
        public FixedListMachineProvisioningLocation<SshMachineLocation> build() {
            if (lm==null)
                return new FixedListMachineProvisioningLocation<SshMachineLocation>(MutableMap.builder()
                    .putIfNotNull("machines", machines)
                    .putIfNotNull("user", user)
                    .putIfNotNull("privateKeyPassphrase", privateKeyPassphrase)
                    .putIfNotNull("privateKeyFile", privateKeyFile)
                    .putIfNotNull("privateKeyData", privateKeyData)
                    .putIfNotNull("localTempDir", localTempDir)
                    .build());
            else
                return lm.createLocation(MutableMap.builder()
                    .putIfNotNull("machines", machines)
                    .putIfNotNull("user", user)
                    .putIfNotNull("privateKeyPassphrase", privateKeyPassphrase)
                    .putIfNotNull("privateKeyFile", privateKeyFile)
                    .putIfNotNull("privateKeyData", privateKeyData)
                    .putIfNotNull("localTempDir", localTempDir)
                    .build(), 
                FixedListMachineProvisioningLocation.class);
        }        
    }
}
