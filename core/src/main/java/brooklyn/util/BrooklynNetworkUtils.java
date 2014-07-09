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
package brooklyn.util;

import java.net.InetAddress;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.location.geo.LocalhostExternalIpLoader;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.net.Networking;

public class BrooklynNetworkUtils {

    /** returns the externally-facing IP address from which this host comes, or 127.0.0.1 if not resolvable */
    public static String getLocalhostExternalIp() {
        return LocalhostExternalIpLoader.getLocalhostIpQuicklyOrDefault();
    }

    /** returns a IP address for localhost paying attention to a system property to prevent lookup in some cases */ 
    public static InetAddress getLocalhostInetAddress() {
        return TypeCoercions.coerce(JavaGroovyEquivalents.elvis(BrooklynServiceAttributes.LOCALHOST_IP_ADDRESS.getValue(), 
                Networking.getLocalHost()), InetAddress.class);
    }

}
