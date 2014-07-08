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
package io.brooklyn.camp.spi.pdp;

import java.util.Map;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.spi.ApplicationComponentTemplate;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.AssemblyTemplate.Builder;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.PlatformTransaction;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;

public class AssemblyTemplateConstructor {

    private final Builder<? extends AssemblyTemplate> builder;
    private final CampPlatform campPlatform;
    protected PlatformTransaction transaction;

    public AssemblyTemplateConstructor(CampPlatform campPlatform) {
        this.campPlatform = campPlatform;
        this.builder = AssemblyTemplate.builder();
        this.transaction = this.campPlatform.transaction();
    }
    
    /** records all the templates to the underlying platform */
    public AssemblyTemplate commit() {
        checkState();
        AssemblyTemplate at = builder.build();
        transaction.add(at).commit();
        transaction = null;
        return at;
    }

    public void name(String name) {
        checkState();
        builder.name(name);
    }

    public void description(String description) {
        checkState();
        builder.description(description);
    }

    public void addCustomAttributes(Map<String, Object> attrs) {
        for (Map.Entry<String, Object> attr : attrs.entrySet())
            builder.customAttribute(attr.getKey(), attr.getValue());
    }

    public void instantiator(Class<? extends AssemblyTemplateInstantiator> instantiator) {
        checkState();
        builder.instantiator(instantiator);
    }
    
    public Class<? extends AssemblyTemplateInstantiator> getInstantiator() {
        checkState();
        return builder.peek().getInstantiator();
    }
    
    public void add(ApplicationComponentTemplate act) {
        checkState();
        builder.add(act);
        transaction.add(act);
    }

    public void add(PlatformComponentTemplate pct) {
        checkState();
        builder.add(pct);
        transaction.add(pct);
    }

    protected void checkState() {
        if (transaction == null)
            throw new IllegalStateException("transaction already committed");
    }

}
