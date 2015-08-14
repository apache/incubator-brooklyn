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
package org.apache.brooklyn.api.location;

import com.google.common.annotations.Beta;

/**
 * A default no-op implementation, which can be extended to override the appropriate methods.
 * 
 * Sub-classing will give the user some protection against future API changes - note that 
 * {@link MachineLocationCustomizer} is marked {@link Beta}.
 */
@Beta
public class BasicMachineLocationCustomizer implements MachineLocationCustomizer {

    @Override
    public void customize(MachineLocation machine) {
        // no-op
    }
    
    @Override
    public void preRelease(MachineLocation machine) {
        // no-op
    }
}
