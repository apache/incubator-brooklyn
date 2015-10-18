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
package org.apache.brooklyn.core.entity;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.core.location.Machines;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

import com.google.common.base.Supplier;

public class EntitySuppliers {

    public static Supplier<SshMachineLocation> uniqueSshMachineLocation(Entity entity) {
        return new UniqueSshMchineLocation(entity);
    }
    
    private static class UniqueSshMchineLocation implements Supplier<SshMachineLocation> {
        private Entity entity;

        private UniqueSshMchineLocation() { /* for xstream */
        }
        
        private UniqueSshMchineLocation(Entity entity) {
            this.entity = entity;
        }
        
        @Override public SshMachineLocation get() {
            return Machines.findUniqueMachineLocation(entity.getLocations(), SshMachineLocation.class).get();
        }
    }
}
