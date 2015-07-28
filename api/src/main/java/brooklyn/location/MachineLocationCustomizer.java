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

import com.google.common.annotations.Beta;

/**
 * Customization hooks to allow apps to perform specific customisation of obtained machines.
 * <p>
 * Users are strongly encouraged to sub-class {@link BasicMachineLocationCustomizer}, to give
 * some protection against this {@link Beta} API changing in future releases.
 */
@Beta
public interface MachineLocationCustomizer {

    /**
     * Override to configure the given machine once it has been created (prior to any use).
     */
    void customize(MachineLocation machine);
    
    /**
     * Override to handle machine-related cleanup prior to {@link MachineProvisioningLocation} 
     * releasing the machine.
     */
    void preRelease(MachineLocation machine);
}
