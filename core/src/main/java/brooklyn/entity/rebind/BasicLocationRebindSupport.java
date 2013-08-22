package brooklyn.entity.rebind;

import static brooklyn.entity.basic.Entities.sanitize;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

import brooklyn.entity.rebind.dto.MementosGenerators;
import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.mementos.LocationMemento;
import brooklyn.util.flags.FlagUtils;

public class BasicLocationRebindSupport implements RebindSupport<LocationMemento> {

    private static final Logger LOG = LoggerFactory.getLogger(BasicLocationRebindSupport.class);
    
    private final AbstractLocation location;
    
    public BasicLocationRebindSupport(AbstractLocation location) {
        this.location = location;
    }
    
    @Override
    public LocationMemento getMemento() {
        return getMementoWithProperties(Collections.<String,Object>emptyMap());
    }

    protected LocationMemento getMementoWithProperties(Map<String,?> props) {
        LocationMemento memento = MementosGenerators.newLocationMementoBuilder(location).customFields(props).build();
    	if (LOG.isTraceEnabled()) LOG.trace("Creating memento for location {}({}): displayName={}; parent={}; children={}; "+
    	        "locationConfig={}; locationConfigDescription={}; customProperties={}; ",
    			new Object[] {memento.getType(), memento.getId(), memento.getDisplayName(), memento.getParent(), 
                memento.getChildren(), sanitize(memento.getLocationConfig()), memento.getLocationConfigDescription(), 
                sanitize(memento.getCustomFields())});
    	return memento;
    }

    @Override
    public void reconstruct(RebindContext rebindContext, LocationMemento memento) {
    	if (LOG.isTraceEnabled()) LOG.trace("Reconstructing location {}({}): displayName={}; parent={}; children={}; " +
    			"locationConfig={}; locationConfigDescription={}; customProperties={}",
    			new Object[] {memento.getType(), memento.getId(), memento.getDisplayName(), memento.getParent(), 
    			memento.getChildren(), sanitize(memento.getLocationConfig()), memento.getLocationConfigDescription(), 
    			sanitize(memento.getCustomFields())});

    	// Note that the flags have been set in the constructor
        location.setName(memento.getDisplayName());
        location.getConfigBag().putAll(memento.getLocationConfig()).markAll(
                Sets.difference(memento.getLocationConfig().keySet(), memento.getLocationConfigUnused())).
                setDescription(memento.getLocationConfigDescription());
        
        // Do late-binding of config that are references to other locations
        for (String flagName : memento.getLocationConfigReferenceKeys()) {
            Field field = FlagUtils.findFieldForFlag(flagName, location);
            Class<?> fieldType = field.getType();
            Object transformedValue = memento.getLocationConfig().get(flagName);
            Object restoredValue = MementoTransformer.transformIdsToLocations(rebindContext, transformedValue, fieldType, true);
            
            location.getConfigBag().putStringKey(flagName, restoredValue);
            FlagUtils.setFieldFromFlag(location, flagName, restoredValue);
        }

        setParent(rebindContext, memento);
        addChildren(rebindContext, memento);
        
        doReconsruct(rebindContext, memento);
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
    
    /**
     * For overriding, to give custom reconsruct behaviour.
     */
    protected void doReconsruct(RebindContext rebindContext, LocationMemento memento) {
        // default is no-op
    }
}
