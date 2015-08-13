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
package org.apache.brooklyn.location;

import java.util.Map;

/**
 * A location that is able to provision new locations within it.
 */
public interface ProvisioningLocation<T extends Location> extends Location {
    /**
     * Obtain a new (sub)-location in the location represented by this class.
     * 
     * @param flags Constraints and details of the location to be provisioned
     * @return the location provisioned
     * @throws LocationNotAvailableException if could not provision such a location
     */
    T obtain(Map<?,?> flags) throws LocationNotAvailableException;

    /**
     * Release a previously-obtained location.
     *
     * @param location a location previously obtained
     * @throws IllegalStateException if the machine did not come from a call to {@link #obtain()} or it has already been released.
     */
    void release(T machine);
    
}
