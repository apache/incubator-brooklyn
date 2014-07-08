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
package io.brooklyn.camp.rest.resource;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.rest.util.CampRestContext;
import io.brooklyn.camp.rest.util.DtoFactory;
import io.brooklyn.camp.rest.util.WebResourceUtils;
import io.brooklyn.camp.spi.AbstractResource;
import io.brooklyn.camp.spi.collection.ResourceLookup;

import javax.servlet.ServletContext;
import javax.ws.rs.core.Context;

public abstract class AbstractCampRestResource {

    // can be injected by jersey when not injected manually
    // (seems there is no way to make this optional so note it _must_ be injected; if needed
    // see notes on workarounds for test frameworks in original AbstractBrooklynRestResource)
    @Context ServletContext servletContext;
    
    private CampRestContext campRestContext;
    
    public synchronized CampRestContext context() {
        if (campRestContext!=null) return campRestContext;
        campRestContext = new CampRestContext(servletContext);
        return campRestContext;
    }
    
    public CampPlatform camp() { return context().camp(); }
    public DtoFactory dto() { return context().dto(); }

    public static <T extends AbstractResource> T lookup(ResourceLookup<T> list, String id) {
        T result = list.get(id);
        if (result==null)
            throw WebResourceUtils.notFound("No such element: %s", id);
        return result;
    }
    
}
