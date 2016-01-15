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
package org.apache.brooklyn.util.net;

import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.annotations.Beta;

@Beta
public interface HasNetworkAddresses {

    /**
     * <h4>note</h4> hostname is something that is set in the operating system.
     * This value may or may not be set in DNS.
     * 
     * @return hostname of the node, or null if unknown
     */
    @Nullable
    String getHostname();
    
    /**
     * All public IP addresses, potentially including shared ips.
     */
    Set<String> getPublicAddresses();

    /**
     * All private IP addresses.
     */
    Set<String> getPrivateAddresses();
}
