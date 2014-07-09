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

import brooklyn.management.ManagementContext;

/**
 * Defines a location, where the {@link #getSpec()} is like a serialized representation
 * of the location so that Brooklyn can create a corresponding location.
 * 
 * Examples include a complete description (e.g. giving a list of machines in a pool), or
 * a name that matches a named location defined in the brooklyn poperties.
 * 
 * Users are not expected to implement this, or to use the interface directly. See
 * {@link LocationRegistry#resolve(String)} and {@link ManagementContext#getLocationRegistry()}.
 */
public interface LocationDefinition {

    public String getId();
    public String getName();
    public String getSpec();
    public Map<String,Object> getConfig();

}