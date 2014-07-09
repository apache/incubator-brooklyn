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
package io.brooklyn.camp.spi;

/** Holds the metadata (name, description, etc) for a CampPlatform.
 * Required to initialize a CampPlatform.
 * <p>
 * See {@link AbstractResource} for more general information.
 */
public class PlatformRootSummary extends AbstractResource {

    public static final String CAMP_TYPE = "Platform";
    
    /** Use {@link #builder()} to create */
    protected PlatformRootSummary() {
    }
    
    // no fields beyond basic resource
    
    //TODO:
    
    // in the DTO
    
//    "supportedFormatsUri": URI, 
//    "extensionsUri": URI,
//    "typeDefinitionsUri": URI,
//    "tags": [ String, + ], ?
//    "specificationVersion": String[], 
//    "implementationVersion": String, ? 
//    "assemblyTemplates": [ Link + ], ? 
//    "assemblies": [ Link + ], ? 
//    "platformComponentTemplates": [ Link + ], ? 
//    "platformComponentCapabilities": [Link + ], ? 
//    "platformComponents": [ Link + ], ?

    
    // builder
    
    public static Builder<? extends PlatformRootSummary> builder() {
        return new Builder<PlatformRootSummary>(CAMP_TYPE);
    }
    
    public static class Builder<T extends PlatformRootSummary> extends AbstractResource.Builder<T,Builder<T>> {
        
        protected Builder(String type) { super(type); }
        
        @SuppressWarnings("unchecked")
        protected T createResource() { return (T) new PlatformRootSummary(); }
        
        protected void initialize() {
            super.initialize();
            // TODO a better way not to have an ID here (new subclass BasicIdentifiableResource for other BasicResource instances)
            id("");
        }
    }

}
