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
package org.apache.brooklyn.camp.spi;

import org.apache.brooklyn.camp.spi.collection.BasicResourceLookup;
import org.apache.brooklyn.camp.spi.collection.ResourceLookup;
import org.apache.brooklyn.camp.spi.collection.ResourceLookup.EmptyResourceLookup;


/** Holds the metadata (name, description, etc) for a PCT
 * as well as fields pointing to behaviour (eg creation of PlatformComponent).
 * <p>
 * See {@link AbstractResource} for more general information.
 */
public class PlatformComponent extends AbstractResource {

    public static final String CAMP_TYPE = "PlatformComponent";
    static { assert CAMP_TYPE.equals(PlatformComponent.class.getSimpleName()); }
    
    /** Use {@link #builder()} to create */
    protected PlatformComponent() {}

    ResourceLookup<ApplicationComponent> applicationComponents;
    ResourceLookup<PlatformComponent> platformComponents;
    String externalManagementUri;
    
    public ResourceLookup<ApplicationComponent> getApplicationComponents() {
        return applicationComponents != null ? applicationComponents : new EmptyResourceLookup<ApplicationComponent>();
    }
    public ResourceLookup<PlatformComponent> getPlatformComponents() {
        return platformComponents != null ? platformComponents : new EmptyResourceLookup<PlatformComponent>();
    }

    private void setApplicationComponents(ResourceLookup<ApplicationComponent> applicationComponents) {
        this.applicationComponents = applicationComponents;
    }
    private void setPlatformComponents(ResourceLookup<PlatformComponent> platformComponents) {
        this.platformComponents = platformComponents;
    }
    
    public String getExternalManagementUri() {
        return externalManagementUri;
    }
    private void setExternalManagementUri(String externalManagementUri) {
        this.externalManagementUri = externalManagementUri;
    }
    
    // builder
    
    public static Builder<? extends PlatformComponent> builder() {
        return new PlatformComponent().new Builder<PlatformComponent>(CAMP_TYPE);
    }
    
    public class Builder<T extends PlatformComponent> extends AbstractResource.Builder<T,Builder<T>> {
        
        protected Builder(String type) { super(type); }
        
        public Builder<T> externalManagementUri(String x) { PlatformComponent.this.setExternalManagementUri(x); return thisBuilder(); }
        public Builder<T> applicationComponentTemplates(ResourceLookup<ApplicationComponent> x) { PlatformComponent.this.setApplicationComponents(x); return thisBuilder(); }
        public Builder<T> platformComponentTemplates(ResourceLookup<PlatformComponent> x) { PlatformComponent.this.setPlatformComponents(x); return thisBuilder(); }
        
        public synchronized Builder<T> add(ApplicationComponent x) {
            if (PlatformComponent.this.applicationComponents==null) {
                PlatformComponent.this.applicationComponents = new BasicResourceLookup<ApplicationComponent>();
            }
            if (!(PlatformComponent.this.applicationComponents instanceof BasicResourceLookup)) {
                throw new IllegalStateException("Cannot add to resource lookup "+PlatformComponent.this.applicationComponents);
            }
            ((BasicResourceLookup<ApplicationComponent>)PlatformComponent.this.applicationComponents).add(x);
            return thisBuilder();
        }
        
        public synchronized Builder<T> add(PlatformComponent x) {
            if (PlatformComponent.this.platformComponents==null) {
                PlatformComponent.this.platformComponents = new BasicResourceLookup<PlatformComponent>();
            }
            if (!(PlatformComponent.this.platformComponents instanceof BasicResourceLookup)) {
                throw new IllegalStateException("Cannot add to resource lookup "+PlatformComponent.this.platformComponents);
            }
            ((BasicResourceLookup<PlatformComponent>)PlatformComponent.this.platformComponents).add(x);
            return thisBuilder();
        }
    }

}
