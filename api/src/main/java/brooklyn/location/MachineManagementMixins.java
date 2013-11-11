package brooklyn.location;

import java.util.Map;

public class MachineManagementMixins {
    
    public interface RichMachineProvisioningLocation<T extends MachineLocation> extends MachineProvisioningLocation<T>, ListsMachines, KillsMachines {}
    
    public interface ListsMachines {
        /** returns map of machine ID to metadata record for all machines known in a given cloud location */ 
        Map<String,MachineMetadata> listMachines();
    }
    
    public interface KillsMachines {
        /** Kills the machine indicated by the given machine id */
        void killMachine(String id);
    }
    
    /** very lightweight machine record */
    public interface MachineMetadata {
        String getId();
        String getName();
        String getPrimaryIp();
        Boolean isRunning();
        /** original metadata object, if available; e.g. ComputeMetadata when using jclouds */ 
        Object getOriginalMetadata();
    }
    
}