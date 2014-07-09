package brooklyn.location;

import java.util.Map;

public class MachineManagementMixins {
    
    public interface RichMachineProvisioningLocation<T extends MachineLocation> extends MachineProvisioningLocation<T>, ListsMachines, GivesMachineMetadata, KillsMachines {}
    
    public interface ListsMachines {
        /** returns map of machine ID to metadata record for all machines known in a given cloud location */ 
        Map<String,MachineMetadata> listMachines();
    }
    
    public interface GivesMachineMetadata {
        /** returns the MachineMetadata for a given (brooklyn) machine location instance, 
         * or null if not matched */
        MachineMetadata getMachineMetadata(MachineLocation location);
    }
    
    public interface KillsMachines {
        /** Kills the indicated machine; throws if not recognised or possible */
        void killMachine(MachineLocation machine);
        
        /** Kills the machine indicated by the given (server-side) machine id;
         *  note, the ID is the _cloud-service_ ID,
         *  that is, pass in getMetadata(machineLocation).getId() not the machineLocation.getId() */
        void killMachine(String cloudServiceId);
    }
    
    /** very lightweight machine record */
    public interface MachineMetadata {
        /** The cloud service ID -- distinct from any Brooklyn {@link Location#getId()} */
        String getId();
        String getName();
        String getPrimaryIp();
        Boolean isRunning();
        /** original metadata object, if available; e.g. ComputeMetadata when using jclouds */ 
        Object getOriginalMetadata();
    }
    
}