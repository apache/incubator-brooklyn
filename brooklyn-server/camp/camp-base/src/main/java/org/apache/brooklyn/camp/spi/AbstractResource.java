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

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.camp.commontypes.RepresentationSkew;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.time.Time;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/** Superclass of CAMP resource implementation objects.
 * Typically used to hold common state of implementation objects
 * and to populate the DTO's used by the REST API.
 * <p>
 * These class instances are typically created using the 
 * static {@link #builder()} methods they contain. 
 * The resulting instances are typically immutable,
 * so where fields can change callers should use a new builder
 * (or update an underlying data store).
 * <p>
 * This class is not meant to be instantiated directly, as
 * CAMP only uses defined subclasses (ie containing these fields).
 * It is instantiable for testing.
 */
public class AbstractResource {

    public static final String CAMP_TYPE = "Resource";
    
    private String id = Identifiers.makeRandomId(8);
    private String name;
    private String type;
    private String description;
    private String sourceCode;
    private Date created = Time.dropMilliseconds(new Date());
    private List<String> tags = Collections.emptyList();
    private RepresentationSkew representationSkew;
    
    private Map<String,Object> customAttributes = new MutableMap<String, Object>();
    
    /** Use {@link #builder()} to create */
    protected AbstractResource() {}
    
    // getters

    public String getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public String getType() {
        return type;
    }
    public String getDescription() {
        return description;
    }
    public String getSourceCode() {
        return sourceCode;
    }
    public Date getCreated() {
        return created;
    }
    public List<String> getTags() {
        return tags;
    }
    public RepresentationSkew getRepresentationSkew() {
        return representationSkew;
    }
    public Map<String, Object> getCustomAttributes() {
        return ImmutableMap.copyOf(customAttributes);
    }
    
    // setters

    private void setId(String id) {
        this.id = id;
    }
    private void setName(String name) {
        this.name = name;
    }
    private void setDescription(String description) {
        this.description = description;
    }
    private void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }
    private void setCreated(Date created) {
        // precision beyond seconds breaks equals check
        this.created = Time.dropMilliseconds(created);
    }
    private void setTags(List<String> tags) {
        this.tags = ImmutableList.copyOf(tags);
    }
    private void setType(String type) {
        this.type = type;
    }
    private void setRepresentationSkew(RepresentationSkew representationSkew) {
        this.representationSkew = representationSkew;
    }
    public void setCustomAttribute(String key, Object value) {
        this.customAttributes.put(key, value);
    }
            
    // builder
    @SuppressWarnings("rawtypes")
    public static Builder<? extends AbstractResource,? extends Builder> builder() {
        return new AbstractResource().new AbstractResourceBuilder(CAMP_TYPE);
    }
    
    /** Builder creates the instance up front to avoid repetition of fields in the builder;
     * but prevents object leakage until build and prevents changes after build,
     * so effectively immutable.
     * <p>
     * Similarly setters in the class are private so those objects are also typically effectively immutable. */
    public abstract class Builder<T extends AbstractResource,U extends Builder<T,U>> {
        
        private boolean built = false;
        private String type = null;
        private boolean initialized = false;
        
        protected Builder(String type) {
            this.type = type;
        }
        
        protected final synchronized void check() {
            if (built) 
                throw new IllegalStateException("Builder instance from "+this+" cannot be access after build");
            if (!initialized) {
                initialized = true;
                initialize();
            }
        }

        protected void initialize() {
            if (type!=null) type(type);
        }

        @SuppressWarnings("unchecked")
        public synchronized T build() {
            check();
            built = true;
            return (T) AbstractResource.this;
        }
        
        @SuppressWarnings("unchecked")
        protected U thisBuilder() { return (U)this; }
        
        public U type(String x) { check(); AbstractResource.this.setType(x); return thisBuilder(); }
        public U id(String x) { check(); AbstractResource.this.setId(x); return thisBuilder(); }
        public U name(String x) { check(); AbstractResource.this.setName(x); return thisBuilder(); }
        public U description(String x) { check(); AbstractResource.this.setDescription(x); return thisBuilder(); }
        public U created(Date x) { check(); AbstractResource.this.setCreated(x); return thisBuilder(); }
        public U tags(List<String> x) { check(); AbstractResource.this.setTags(x); return thisBuilder(); }
        public U representationSkew(RepresentationSkew x) { check(); AbstractResource.this.setRepresentationSkew(x); return thisBuilder(); }
        public U customAttribute(String key, Object value) { check(); AbstractResource.this.setCustomAttribute(key, value); return thisBuilder(); }
        public U sourceCode(String x) { check(); AbstractResource.this.setSourceCode(x); return thisBuilder(); }

//        public String type() { return instance().type; }
    }
    
    @VisibleForTesting
    protected class AbstractResourceBuilder extends Builder<AbstractResource,AbstractResourceBuilder> {
        protected AbstractResourceBuilder(String type) {
            super(type);
        }
    }

    @Override
    public String toString() {
        return super.toString()+"[id="+getId()+"; type="+getType()+"]";
    }
}
