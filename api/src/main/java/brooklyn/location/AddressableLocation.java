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

import java.net.InetAddress;

/** A location that has an IP address.
 * <p>
 * This IP address may be a machine (usually the MachineLocation sub-interface), 
 * or often an entry point for a service.
 */
public interface AddressableLocation extends Location {

    /**
     * Return the single most appropriate address for this location.
     * (An implementation or sub-interface definition may supply more information
     * on the precise semantics of the address.)
     * 
     * Should not return null, but in some "special cases" (e.g. CloudFoundryLocation it
     * may return null if the location is not configured correctly). Users should expect
     * a non-null result and treat null as a programming error or misconfiguration. 
     * Implementors of this interface should strive to not return null (and then we'll
     * remove this caveat from the javadoc!).
     */
    InetAddress getAddress();

}
