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
package brooklyn.camp.lite;

import io.brooklyn.camp.BasicCampPlatform;
import brooklyn.camp.brooklyn.api.HasBrooklynManagementContext;
import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.management.ManagementContext;

public class CampPlatformWithJustBrooklynMgmt extends BasicCampPlatform implements HasBrooklynManagementContext {

    private ManagementContext mgmt;

    public CampPlatformWithJustBrooklynMgmt(ManagementContext mgmt) {
        this.mgmt = mgmt;
        ((BrooklynProperties)mgmt.getConfig()).put(BrooklynServerConfig.CAMP_PLATFORM, this);
    }
    
    @Override
    public ManagementContext getBrooklynManagementContext() {
        return mgmt;
    }

}
