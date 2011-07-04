package brooklyn.location;

/**
 * A location that is able to provision new machines within its location.
 *
 * This interface extends @{link Location} to add the ability to provision @{link MachineLocation}s in this location.
 */
public interface MachineProvisioningLocation<T extends MachineLocation> extends Location {

    /**
     * Obtain a machine in this location.
     * @return a machine that is a child of this location.
     * @throws NoMachinesAvailableException if there are no machines available in this location.
     */
    public T obtain() throws NoMachinesAvailableException;

    /**
     * Release a previously-obtained machine.
     * @param machine a @{link MachineLocation} previously obtained from a call to @{link obtain}
     */
    public void release(T machine);

}
