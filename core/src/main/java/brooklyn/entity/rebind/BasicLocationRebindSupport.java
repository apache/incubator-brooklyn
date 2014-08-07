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
package brooklyn.entity.rebind;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.rebind.dto.MementosGenerators;
import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.mementos.LocationMemento;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.collect.Sets;

public class BasicLocationRebindSupport extends AbstractBrooklynObjectRebindSupport<LocationMemento> {

    private static final Logger LOG = LoggerFactory.getLogger(BasicLocationRebindSupport.class);
    
    private final AbstractLocation location;
    
    public BasicLocationRebindSupport(AbstractLocation location) {
        super(location);
        this.location = location;
    }
    
    // Can rely on super-type once the deprecated getMementoWithProperties is deleted
    @Override
    public LocationMemento getMemento() {
        return getMementoWithProperties(Collections.<String,Object>emptyMap());
    }

    /**
     * @deprecated since 0.7.0; use generic config/attributes rather than "custom fields", so use {@link #getMemento()}
     */
    @Deprecated
    protected LocationMemento getMementoWithProperties(Map<String,?> props) {
        LocationMemento memento = MementosGenerators.newLocationMementoBuilder(location).customFields(props).build();
    	if (LOG.isTraceEnabled()) LOG.trace("Creating memento for location: {}", memento.toVerboseString());
    	return memento;
    }

    @Override
    protected void addConfig(RebindContext rebindContext, LocationMemento memento) {
        // FIXME Treat config like we do for entities; this code will disappear when locations become entities.
        
        // Note that the flags have been set in the constructor
        // FIXME Relies on location.getLocalConfigBag being mutable (to modify the location's own config)
        
        location.getLocalConfigBag().putAll(memento.getLocationConfig()).markAll(
                Sets.difference(memento.getLocationConfig().keySet(), memento.getLocationConfigUnused())).
                setDescription(memento.getLocationConfigDescription());

        for (Map.Entry<String, Object> entry : memento.getLocationConfig().entrySet()) {
            String flagName = entry.getKey();
            try {
                Field field = FlagUtils.findFieldForFlag(flagName, location);
                Class<?> fieldType = field.getType();
                Object value = entry.getValue();
                
                // Field is either of type ConfigKey, or it's a vanilla java field annotated with @SetFromFlag.
                // If the former, need to look up the field value (i.e. the ConfigKey) to find out the type.
                // If the latter, just want to look at the field itself to get the type.
                // Then coerce the value we have to that type.
                // And use magic of setFieldFromFlag's magic to either set config or field as appropriate.
                if (ConfigKey.class.isAssignableFrom(fieldType)) {
                    ConfigKey<?> configKey = (ConfigKey<?>) FlagUtils.getField(location, field);
                    value = TypeCoercions.coerce(entry.getValue(), configKey.getType());
                } else {
                    value = TypeCoercions.coerce(entry.getValue(), fieldType);
                }
                if (value != null) {
                    location.getLocalConfigBag().putStringKey(flagName, value);
                    FlagUtils.setFieldFromFlag(location, flagName, value);
                }
            } catch (NoSuchElementException e) {
                // FIXME How to do findFieldForFlag without throwing exception if it's not there?
            }
        }
    }
    
    @Override
    protected void addCustoms(RebindContext rebindContext, LocationMemento memento) {
        setParent(rebindContext, memento);
        addChildren(rebindContext, memento);
        location.init(); // TODO deprecated calling init; will be deleted
    }

    protected void addChildren(RebindContext rebindContext, LocationMemento memento) {
        for (String childId : memento.getChildren()) {
            Location child = rebindContext.getLocation(childId);
            if (child != null) {
            	location.addChild(child);
            } else {
            	LOG.warn("Ignoring child {} of location {}({}), as cannot be found", new Object[] {childId, memento.getType(), memento.getId()});
            }
        }
    }

    protected void setParent(RebindContext rebindContext, LocationMemento memento) {
        Location parent = (memento.getParent() != null) ? rebindContext.getLocation(memento.getParent()) : null;
        if (parent != null) {
            location.setParent(parent);
        } else if (memento.getParent() != null) {
        	LOG.warn("Ignoring parent {} of location {}({}), as cannot be found", new Object[] {memento.getParent(), memento.getType(), memento.getId()});
        }
    }
}
