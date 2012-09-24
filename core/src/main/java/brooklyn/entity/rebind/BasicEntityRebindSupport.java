package brooklyn.entity.rebind;

import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.mementos.EntityMemento;

public class BasicEntityRebindSupport implements RebindSupport<EntityMemento> {

    protected static final Logger LOG = LoggerFactory.getLogger(BasicEntityRebindSupport.class);
    
    private final EntityLocal entity;
    
    public BasicEntityRebindSupport(EntityLocal entity) {
        this.entity = entity;
    }
    
    @Override
    public EntityMemento getMemento() {
        return getMementoWithProperties(Collections.<String,Object>emptyMap());
    }

    protected EntityMemento getMementoWithProperties(Map<String,?> props) {
        EntityMemento memento = new BasicEntityMemento(entity, props);
    	if (LOG.isDebugEnabled()) LOG.debug("Creating memento for entity {}({}): config={}; attributes={}; properties={}; parent={}; children={}; locations={}", 
    			new Object[] {memento.getType(), memento.getId(), memento.getConfig(), memento.getAttributes(), memento.getProperties(), memento.getParent(), memento.getChildren(), memento.getLocations()});
    	return memento;
    }

    @Override
    public void reconstruct(EntityMemento memento) {
    	if (LOG.isDebugEnabled()) LOG.debug("Reconstructing entity {}({}): config={}; attributes={}; properties={}", new Object[] {memento.getType(), memento.getId(), memento.getConfig(), memento.getAttributes(), memento.getProperties()});
    	
        // Note that the id should have been set in the constructor; it is immutable
        entity.setDisplayName(memento.getDisplayName());

        for (Map.Entry<ConfigKey, Object> entry : memento.getConfig().entrySet()) {
            entity.setConfig(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<AttributeSensor, Object> entry : memento.getAttributes().entrySet()) {
            entity.setAttribute(entry.getKey(), entry.getValue());
        }
    }
    
    @Override
    public void rebind(RebindContext rebindContext, EntityMemento memento) {
    	if (LOG.isDebugEnabled()) LOG.debug("Rebinding entity {}({}): parent={}; children={}; locations={}", new Object[] {memento.getType(), memento.getId(), memento.getParent(), memento.getChildren(), memento.getLocations()});
    	
        setParent(rebindContext, memento);
        addChildren(rebindContext, memento);
        addMembers(rebindContext, memento);
        addLocations(rebindContext, memento);
        doRebind(rebindContext, memento);
    }

    /**
     * For overriding, to give custom rebind behaviour.
     */
    protected void doRebind(RebindContext rebindContext, EntityMemento memento) {
        // default is no-op
    }
    
    protected void addMembers(RebindContext rebindContext, EntityMemento memento) {
        if (memento.getMembers().size() > 0) {
            if (entity instanceof Group) {
                for (String memberId : memento.getMembers()) {
                    Entity member = rebindContext.getEntity(memberId);
                    ((Group)entity).addMember(member);
                }
            } else {
                throw new UnsupportedOperationException("Entity with members should be a group, and override rebindMembers: entity="+entity+"; members="+memento.getMembers());
            }
        }
    }
    
    protected void addChildren(RebindContext rebindContext, EntityMemento memento) {
        for (String childId : memento.getChildren()) {
            Entity child = rebindContext.getEntity(childId);
            entity.addOwnedChild(child);
        }
    }

    protected void setParent(RebindContext rebindContext, EntityMemento memento) {
        Entity parent = (memento.getParent() != null) ? rebindContext.getEntity(memento.getParent()) : null;
        if (parent != null) {
            entity.setOwner(parent);
        }
    }
    
    protected void addLocations(RebindContext rebindContext, EntityMemento memento) {
        for (String locationId : memento.getLocations()) {
            entity.getLocations().add(rebindContext.getLocation(locationId));
        }
    }
}
