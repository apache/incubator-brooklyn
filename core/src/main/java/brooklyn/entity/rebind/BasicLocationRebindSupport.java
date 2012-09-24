package brooklyn.entity.rebind;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.mementos.LocationMemento;
import brooklyn.util.flags.FlagUtils;

public class BasicLocationRebindSupport implements RebindSupport<LocationMemento> {

	// FIXME Filter passwords from log messages!
	
    protected static final Logger LOG = LoggerFactory.getLogger(BasicLocationRebindSupport.class);
    
    private final AbstractLocation location;
    
    public BasicLocationRebindSupport(AbstractLocation location) {
        this.location = location;
    }
    
    @Override
    public LocationMemento getMemento() {
    	Map<String, ? extends Object> flags = FlagUtils.getFieldsWithFlags(location);
        return getMementoWithProperties(flags);
    }

    protected LocationMemento getMementoWithProperties(Map<String,?> props) {
        LocationMemento memento = new BasicLocationMemento(location, props);
    	if (LOG.isDebugEnabled()) LOG.debug("Creating memento for location {}({}): config={}; attributes={}; properties={}; parent={}; children={}",
    			new Object[] {memento.getType(), memento.getId(), memento.getDisplayName(), memento.getProperties(), memento.getParent(), memento.getChildren()});
    	return memento;
    }

    @Override
    public void reconstruct(LocationMemento memento) {
    	if (LOG.isDebugEnabled()) LOG.debug("Reconstructing location {}({}): displayName={}; properties={}", new Object[] {memento.getType(), memento.getId(), memento.getDisplayName(), memento.getProperties()});

    	// Note that the id should have been set in the constructor; it is immutable
        location.setName(memento.getDisplayName());
        FlagUtils.setFieldsFromFlags(memento.getProperties(), location);
    }
    
    @Override
    public void rebind(RebindContext rebindContext, LocationMemento memento) {
    	if (LOG.isDebugEnabled()) LOG.debug("Rebinding location {}({}): parent={}; children={}", new Object[] {memento.getType(), memento.getId(), memento.getParent(), memento.getChildren()});
    	
        setParent(rebindContext, memento);
        addChildren(rebindContext, memento);
        doRebind(rebindContext, memento);
    }

    /**
     * For overriding, to give custom rebind behaviour.
     */
    protected void doRebind(RebindContext rebindContext, LocationMemento memento) {
        // default is no-op
    }
    
    protected void addChildren(RebindContext rebindContext, LocationMemento memento) {
        for (String childId : memento.getChildren()) {
            Location child = rebindContext.getLocation(childId);
            location.addChildLocation(child);
        }
    }

    protected void setParent(RebindContext rebindContext, LocationMemento memento) {
        Location parent = (memento.getParent() != null) ? rebindContext.getLocation(memento.getParent()) : null;
        if (parent != null) {
            location.setParentLocation(parent);
        }
    }
}
