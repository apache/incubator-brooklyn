package brooklyn.location.basic;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static brooklyn.util.GroovyJavaMethods.truth;

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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * A provisioner of {@link MachineLocation}s which takes a list of machines it can connect to.
 * The collection of initial machines should be supplied in the 'machines' flag in the constructor,
 * for example a list of machines which can be SSH'd to. 
 * 
 * This can be extended to have a mechanism to make more machines to be available
 * (override provisionMore and canProvisionMore).
 */
//TODO combine with jclouds BYON
public class FixedListMachineProvisioningLocation<T extends MachineLocation> extends AbstractLocation implements MachineProvisioningLocation<T>, CoordinatesProvider {

    private Object lock;
    @SetFromFlag("machines")
    protected Set<T> machines;
    protected Set<T> inUse;

    public FixedListMachineProvisioningLocation() {
        this(Maps.newLinkedHashMap());
    }
    public FixedListMachineProvisioningLocation(Map properties) {
        super(properties);

        for (MachineLocation location: machines) {
            // FIXME Bad casting
            Location location2 = (Location) location;
            Location parent = location2.getParentLocation();
            if (parent != null && !parent.equals(this))
                throw new IllegalStateException("Machines must not have a parent location, but machine '"+location2.getName()+"' has its parent location set");
	        addChildLocation(location2);
	        location2.setParentLocation(this);
        }
    }

    protected void configure(Map properties) {
        if (!truth(lock)) {
            lock = new Object();
            machines = Sets.newLinkedHashSet();
            inUse = Sets.newLinkedHashSet();
        }
        super.configure(properties);
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
     
    @Override
    protected void addChildLocation(Location child) {
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
        synchronized (lock) {
            Set<T> a = getAvailable();
            if (!truth(a)) {
                if (canProvisionMore()) {
                    provisionMore(1);
                    a = getAvailable();
                }
                if (!truth(a))
                    throw new NoMachinesAvailableException(this);
            }
            machine = a.iterator().next();
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
        }
    }

    @Override
    public Map<String,Object> getProvisioningFlags(Collection<String> tags) {
        return Maps.<String,Object>newLinkedHashMap();
    }
    
    /**
     * Facilitates fluent/programmatic style for constructing a fixed pool of machines.
     * <code>
     * new FixedListMachineProvisioningLocation.Builder().
                user("alex").
                keyFile("/Users/alex/.ssh/id_rsa").
                addAddress("10.0.0.1").
                addAddress("10.0.0.2").
                addAddress("10.0.0.3").
                addAddressMultipleTimes("127.0.0.1", 5).
                build();
     * </code>
     */
    public static class Builder {
        String user;
        String privateKeyFile;
        String privateKeyData;
        List machines = Lists.newArrayList();
        public Builder user(String user) {
            this.user = user;
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
        /** adds the locations; user and keyfile set in the builder are _not_ applied to the machine
         * (use add(String address) for that)
         */
        public Builder add(SshMachineLocation location) {
            machines.add(location);
            return this;
        }
        public Builder addAddress(String address) {
            Map config = MutableMap.of("address",  address);
            if (truth(user)) config.put("sshconfig.user", user);
            if (truth(privateKeyFile)) config.put("sshconfig.privateKeyFile", privateKeyFile);
            if (truth(privateKeyData)) config.put("sshconfig.privateKey", privateKeyData);
            add(new SshMachineLocation(config)); 
            return this;
        }
        public Builder addAddressMultipleTimes(String address, int n) {
            Map config = MutableMap.of("address", address);
            if (truth(user)) config.put("sshconfig.user", user);
            if (truth(privateKeyFile)) config.put("sshconfig.privateKeyFile", privateKeyFile);
            if (truth(privateKeyData)) config.put("sshconfig.privateKey", privateKeyData);
            for (int i=0; i<n; i++)
                add(new SshMachineLocation(new MutableMap(config))); 
            return this;
        }
        public FixedListMachineProvisioningLocation build() {
            return new FixedListMachineProvisioningLocation(MutableMap.builder()
                    .put("machines", machines)
                    .put("user", user)
                    .put("privateKeyFile", privateKeyFile)
                    .put("privateKeyData", privateKeyData)
                    .build());
        }        
    }
} 
