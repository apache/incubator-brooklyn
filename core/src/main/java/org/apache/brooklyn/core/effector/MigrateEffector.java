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
package org.apache.brooklyn.core.effector;

import com.google.common.annotations.Beta;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.annotation.EffectorParam;

@Beta
public interface MigrateEffector {

    MethodEffector<Void> MIGRATE = new MethodEffector<Void>(MigrateEffector.class, "migrate");
    String MIGRATE_LOCATION_SPEC = "locationSpec";

    /**
     * Starts a migration process.
     * It calls stop() on the original locations and start() on the new one.
     * <p/>
     * After this process finishes it refreshes all the sibling entities dependent data (ConfigKeys, Env variables...)
     */
    @Beta
    @Effector(description = "Migrates the current entity to another location. It will free the provisioned resources" +
            " used by the former location")
    void migrate(@EffectorParam(name = MIGRATE_LOCATION_SPEC, description = "Location Spec", nullable = false) String locationSpec);
}
