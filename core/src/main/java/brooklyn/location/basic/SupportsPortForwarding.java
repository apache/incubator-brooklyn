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
package brooklyn.location.basic;

import brooklyn.util.net.Cidr;

import com.google.common.net.HostAndPort;

public interface SupportsPortForwarding {

    /** returns an endpoint suitable for contacting the indicated private port on this object,
     * from the given Cidr, creating it if necessary and possible; 
     * may return null if forwarding not available 
     */
    public HostAndPort getSocketEndpointFor(Cidr accessor, int privatePort);
    
    /** marker on a location to indicate that port forwarding should be done automatically
     * for attempts to access from Brooklyn
     */
    public interface RequiresPortForwarding extends SupportsPortForwarding {
    }
    
}
