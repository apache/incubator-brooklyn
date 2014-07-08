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
package brooklyn.entity.rebind.dto;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import brooklyn.entity.basic.Entities;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.TreeNode;
import brooklyn.util.config.ConfigBag;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * The persisted state of a location.
 * 
 * @author aled
 */
public class BasicLocationMemento extends AbstractTreeNodeMemento implements LocationMemento, Serializable {

    private static final long serialVersionUID = -4025337943126838761L;
    
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractTreeNodeMemento.Builder<Builder> {
        protected Map<String,Object> locationConfig = Maps.newLinkedHashMap();
        protected Set<String> locationConfigUnused = Sets.newLinkedHashSet();
        protected String locationConfigDescription;
        
        public Builder from(LocationMemento other) {
            super.from((TreeNode)other);
            displayName = other.getDisplayName();
            locationConfig.putAll(other.getLocationConfig());
            locationConfigUnused.addAll(other.getLocationConfigUnused());
            locationConfigDescription = other.getLocationConfigDescription();
            fields.putAll(other.getCustomFields());
            return self();
        }
        public LocationMemento build() {
            return new BasicLocationMemento(this);
        }
        public void copyConfig(ConfigBag config) {
            locationConfig.putAll(config.getAllConfig());
            locationConfigUnused.addAll(config.getUnusedConfig().keySet());
            locationConfigDescription = config.getDescription();
        }
    }
    
    private Map<String,Object> locationConfig;
	private Set<String> locationConfigUnused;
	private String locationConfigDescription;

    // Trusts the builder to not mess around with mutability after calling build()
	protected BasicLocationMemento(Builder builder) {
	    super(builder);
	    locationConfig = toPersistedMap(builder.locationConfig);
	    locationConfigUnused = toPersistedSet(builder.locationConfigUnused);
	    locationConfigDescription = builder.locationConfigDescription;
	}
	
    @Override
    public Map<String,Object> getLocationConfig() {
		return fromPersistedMap(locationConfig);
	}
	
    @Override
    public Set<String> getLocationConfigUnused() {
		return fromPersistedSet(locationConfigUnused);
	}
    
    @Override
    public String getLocationConfigDescription() {
        return locationConfigDescription;
    }
    
    @Override
    protected ToStringHelper newVerboseStringHelper() {
        return super.newVerboseStringHelper()
                .add("config", Entities.sanitize(getLocationConfig()))
                .add("locationConfigDescription", getLocationConfigDescription());
    }
}
