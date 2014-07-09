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
package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.spi.PlatformRootSummary;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.annotations.Beta;

/** launcher for {@link BrooklynCampPlatform}, which may or may not start a (web) server depending on children */
@Beta
public abstract class BrooklynCampPlatformLauncherAbstract {

    protected BrooklynCampPlatform platform;
    protected ManagementContext mgmt;
    
    public BrooklynCampPlatformLauncherAbstract useManagementContext(ManagementContext mgmt) {
        if (this.mgmt!=null && mgmt!=this.mgmt)
            throw new IllegalStateException("Attempt to change mgmt context; not supported.");
        
        this.mgmt = mgmt;
        
        return this;
    }
    
    public BrooklynCampPlatformLauncherAbstract launch() {
        if (platform!=null)
            throw new IllegalStateException("platform already created");

        if (getBrooklynMgmt()==null)
            useManagementContext(newMgmtContext());
        
        platform = new BrooklynCampPlatform(
                PlatformRootSummary.builder().name("Brooklyn CAMP Platform").build(),
                getBrooklynMgmt())
            .setConfigKeyAtManagmentContext();
        
        return this;
    }

    protected LocalManagementContext newMgmtContext() {
        return new LocalManagementContext();
    }

    public ManagementContext getBrooklynMgmt() {
        return mgmt;
    }
    
    public BrooklynCampPlatform getCampPlatform() {
        return platform;
    }

    /** stops any servers (camp and brooklyn) launched by this launcher */
    public abstract void stopServers() throws Exception;

}
