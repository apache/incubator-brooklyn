package brooklyn.location.basic

import java.util.Arrays;
import java.util.Collection
import java.util.Map

import brooklyn.location.CoordinatesProvider
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.NoMachinesAvailableException
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag

import com.google.common.base.Preconditions

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
    @SetFromFlag('machines')
    protected Set<T> machines;
    protected Set<T> inUse;

    public FixedListMachineProvisioningLocation(Map properties = [:]) {
        super(properties)

        for (SshMachineLocation location: machines) {
            if (location.parentLocation != null && !location.parentLocation.equals(this))
                throw new IllegalStateException("Machines must not have a parent location, but machine '"+location.name+"' has its parent location set");
	        addChildLocation(location)
	        location.setParentLocation(this)
        }
    }

    protected void configure(Map properties) {
        if (!lock) {
            lock = new Object();
            machines = []
            inUse = []
        }
        super.configure(properties);
    }
    
    public double getLatitude() {
        return leftoverProperties.latitude;
    }
    
    public double getLongitude() {
        return leftoverProperties.longitude;
    }

    public Set getAvailable() {
        Set a = []
        a.addAll(machines)
        a.removeAll(inUse)
        a
    }   
     
    @Override
    protected void addChildLocation(Location child) {
        super.addChildLocation(child);
        machines.add(child);
    }

    @Override
    protected boolean removeChildLocation(Location child) {
        if (inUse.contains(child)) {
            throw new IllegalStateException("Child location $child is in use; cannot remove from $this");
        }
        machines.remove(child);
        return super.removeChildLocation(child);
    }

    public boolean canProvisionMore() { return false; }
    public void provisionMore(int size) { throw new IllegalStateException("more not permitted"); }
    
    public T obtain(Map<String,? extends Object> flags=[:]) {
        T machine;
        synchronized (lock) {
            Set a = getAvailable()
            if (!a) {
                if (canProvisionMore()) {
                    provisionMore(1);
                    a = getAvailable();
                }
                if (!a)
                    throw new NoMachinesAvailableException(this);
            }
            machine = a.iterator().next();
            inUse.add(machine);
        }
        return machine;
    }

    public void release(T machine) {
        synchronized (lock) {
            if (inUse.contains(machine) == false)
                throw new IllegalStateException("Request to release machine $machine, but this machine is not currently allocated")
            inUse.remove(machine);
        }
    }
    
    Map<String,Object> getProvisioningFlags(Collection<String> tags) {
        return [:]
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
        List machines = [];
        public Builder user(String user) {
            this.user = user;
            this;
        }
        public Builder keyFile(String keyFile) {
            this.privateKeyFile = keyFile;
            this; 
        }
        public Builder keyData(String keyData) {
            this.privateKeyData = keyData;
            this;
        }
        /** adds the locations; user and keyfile set in the builder are _not_ applied to the machine
         * (use add(String address) for that)
         */
        public Builder add(SshMachineLocation location) {
            machines << location;
            this;
        }
        public Builder addAddress(String address) {
            Map config = [address: address];
            if (user) config.put("sshconfig.user", user);
            if (privateKeyFile) config.put("sshconfig.privateKeyFile", privateKeyFile);
            if (privateKeyData) config.put("sshconfig.privateKey", privateKeyData);
            add(new SshMachineLocation(config)); 
            this;
        }
        public Builder addAddressMultipleTimes(String address, int n) {
            Map config = [address: address];
            if (user) config.put("sshconfig.user", user);
            if (privateKeyFile) config.put("sshconfig.privateKeyFile", privateKeyFile);
            if (privateKeyData) config.put("sshconfig.privateKey", privateKeyData);
            for (int i=0; i<n; i++)
                add(new SshMachineLocation(new MutableMap(config))); 
            this;
        }
        public FixedListMachineProvisioningLocation build() {
            new FixedListMachineProvisioningLocation(machines: machines);
        }        
    }
} 
