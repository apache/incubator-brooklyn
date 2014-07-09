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


/** Holds the metadata (name, description, etc) for a PCT
 * as well as fields pointing to behaviour (eg creation of PlatformComponent).
 * <p>
 * See {@link AbstractResource} for more general information.
 */
public class PlatformComponentTemplate extends AbstractResource {

    public static final String CAMP_TYPE = "PlatformComponentTemplate";
    static { assert CAMP_TYPE.equals(PlatformComponentTemplate.class.getSimpleName()); }
    
    /** Use {@link #builder()} to create */
    protected PlatformComponentTemplate() {}

    
    // no fields beyond basic resource
    
    
    // builder
    
    public static Builder<? extends PlatformComponentTemplate> builder() {
        return new Builder<PlatformComponentTemplate>(CAMP_TYPE);
    }
    
    public static class Builder<T extends PlatformComponentTemplate> extends AbstractResource.Builder<T,Builder<T>> {
        
        protected Builder(String type) { super(type); }
        
        @SuppressWarnings("unchecked")
        protected T createResource() { return (T) new PlatformComponentTemplate(); }
        
//        public Builder<T> foo(String x) { instance().setFoo(x); return thisBuilder(); }
    }

}
