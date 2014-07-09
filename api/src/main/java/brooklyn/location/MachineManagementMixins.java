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