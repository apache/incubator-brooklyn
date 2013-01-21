package brooklyn.location.basic;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static brooklyn.util.GroovyJavaMethods.truth;

import java.io.Closeable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.location.CoordinatesProvider;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.text.WildcardGlobs;
import brooklyn.util.text.WildcardGlobs.PhraseTreatment;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;

/**
 * A provisioner of {@link MachineLocation}s which takes a list of machines it can connect to.
 * The collection of initial machines should be supplied in the 'machines' flag in the constructor,
 * for example a list of machines which can be SSH'd to. 
 * 
 * This can be extended to have a mechanism to make more machines to be available
 * (override provisionMore and canProvisionMore).
 */
//TODO combine with jclouds BYON
public class FixedListMachineProvisioningLocation<T extends MachineLocation> extends AbstractLocation implements MachineProvisioningLocation<T>, CoordinatesProvider, Closeable {

    // TODO Synchronization looks very wrong for accessing machines/inUse 
    // e.g. removeChildLocation doesn't synchronize when doing machines.remove(...),
    // and getMachines() and getInUse() return the real sets risking 
    // ConcurrentModificationException in the caller if it iterates over them etc.
    
    private Object lock;
    
    @SetFromFlag
    protected Set<T> machines;
    
    @SetFromFlag
    protected Set<T> inUse;

    @SetFromFlag
    protected Set<T> pendingRemoval;
    
    @SetFromFlag
    protected File localTempDir;
    
    public FixedListMachineProvisioningLocation() {
        this(Maps.newLinkedHashMap());
    }
    public FixedListMachineProvisioningLocation(Map properties) {
        super(properties);

        for (MachineLocation location: machines) {
            // FIXME Bad casting
            Location machine = (Location) location;
            Location parent = machine.getParentLocation();
            if (parent != null && !parent.equals(this))
                throw new IllegalStateException("Machines must not have a parent location, but machine '"+machine.getName()+"' has its parent location set");
	        addChildLocation(machine);
        }
    }

    protected void configure(Map properties) {
        if (lock == null) {
            lock = new Object();
            machines = Sets.newLinkedHashSet();
            inUse = Sets.newLinkedHashSet();
            pendingRemoval = Sets.newLinkedHashSet();
        }
        super.configure(properties);
    }
    
    @Override
    public void close() {
        for (T machine : machines) {
            if (machine instanceof Closeable) Closeables.closeQuietly((Closeable)machine);
        }
    }
    
    public void addMachine(T machine) {
        synchronized (lock) {
            if (machines.contains(machine)) {
                throw new IllegalArgumentException("Cannot add "+machine+" to "+toString()+", because already contained");
            }
            
            Location existingParent = ((Location)machine).getParentLocation();
            if (existingParent != null && !existingParent.equals(this))
                throw new IllegalStateException("Machine "+machine+" must not have a parent location to be added to "+toString()+", but parent is already set to '"+existingParent+"'");
            addChildLocation((Location)machine);
            
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
                removeChildLocation((Location)machine);
            }
        }
    }
    
    protected Set<T> getMachines() {
        return machines;
    }
    
    protected Set<T> getInUse() {
        return inUse;
    }
    
    public double getLatitude() {
        return (Double) elvis(leftoverProperties.get("latitude"), 0);
    }
    
    public double getLongitude() {
        return (Double) elvis(leftoverProperties.get("longitude"), 0);
    }

    public Set<T> getAvailable() {
        Set<T> a = Sets.newLinkedHashSet(machines);
        a.removeAll(inUse);
        return a;
    }   
     
    public Set<T> getAllMachines() {
        return ImmutableSet.copyOf(machines);
    }   
     
    @Override
    public void addChildLocation(Location child) {
        super.addChildLocation(child);
        machines.add((T)child);
    }

    @Override
    protected boolean removeChildLocation(Location child) {
        if (inUse.contains(child)) {
            throw new IllegalStateException("Child location "+child+" is in use; cannot remove from "+this);
        }
        machines.remove(child);
        return super.removeChildLocation(child);
    }

    public boolean canProvisionMore() { return false; }
    public void provisionMore(int size) { throw new IllegalStateException("more not permitted"); }

    public T obtain() throws NoMachinesAvailableException {
        return obtain(Maps.<String,Object>newLinkedHashMap());
    }
    
    @Override
    public T obtain(Map<String,? extends Object> flags) throws NoMachinesAvailableException {
        T machine;
        T desiredMachine = (T) flags.get("desiredMachine");
        
        synchronized (lock) {
            Set<T> a = getAvailable();
            if (a.isEmpty()) {
                if (canProvisionMore()) {
                    provisionMore(1);
                    a = getAvailable();
                }
                if (a.isEmpty())
                    throw new NoMachinesAvailableException(this);
            }
            if (desiredMachine != null) {
                if (a.contains(desiredMachine)) {
                    machine = desiredMachine;
                } else {
                    throw new IllegalStateException("Desired machine "+desiredMachine+" not available in "+toString()+"; "+
                            (inUse.contains(desiredMachine) ? "machine in use" : "machine unknown"));
                }
            } else {
                machine = a.iterator().next();
            }
            inUse.add(machine);
        }
        return machine;
    }

    @Override
    public void release(T machine) {
        synchronized (lock) {
            if (inUse.contains(machine) == false)
                throw new IllegalStateException("Request to release machine "+machine+", but this machine is not currently allocated");
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
        String user;
        String privateKeyPassphrase;
        String privateKeyFile;
        String privateKeyData;
        File localTempDir;

        List machines = Lists.newArrayList();
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
                add(new SshMachineLocation(makeConfig(addr))); 
            return this;
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
        public FixedListMachineProvisioningLocation build() {
            return new FixedListMachineProvisioningLocation(MutableMap.builder()
                    .put("machines", machines)
                    .put("user", user)
                    .put("privateKeyPassphrase", privateKeyPassphrase)
                    .put("privateKeyFile", privateKeyFile)
                    .put("privateKeyData", privateKeyData)
                    .put("localTempDir", localTempDir)
                    .build());
        }        
    }
} 
