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
package org.apache.brooklyn.camp.brooklyn.spi.lookup;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.spi.Assembly;
import org.apache.brooklyn.camp.spi.PlatformRootSummary;
import org.apache.brooklyn.camp.spi.collection.ResolvableLink;


public class AssemblyBrooklynLookup extends AbstractBrooklynResourceLookup<Assembly> {

    private PlatformComponentBrooklynLookup pcs;

    public AssemblyBrooklynLookup(PlatformRootSummary root, ManagementContext bmc, PlatformComponentBrooklynLookup pcs) {
        super(root, bmc);
        this.pcs = pcs;
    }

    @Override
    public Assembly get(String id) {
        Entity entity = bmc.getEntityManager().getEntity(id);
        if (!(entity instanceof Application))
            throw new IllegalArgumentException("Element for "+id+" is not an Application ("+entity+")");
        Assembly.Builder<? extends Assembly> builder = Assembly.builder()
                .created(new Date(entity.getCreationTime()))
                .id(entity.getId())
                .name(entity.getDisplayName());
        
        builder.customAttribute("externalManagementUri", BrooklynUrlLookup.getUrl(bmc, entity));
        
        for (Entity child: entity.getChildren())
            // FIXME this walks the whole damn tree!
            builder.add( pcs.get(child.getId() ));
        return builder.build();
    }

    @Override
    public List<ResolvableLink<Assembly>> links() {
        List<ResolvableLink<Assembly>> result = new ArrayList<ResolvableLink<Assembly>>();
        for (Application app: bmc.getApplications())
            result.add(newLink(app.getId(), app.getDisplayName()));
        return result;
    }

}
