package brooklyn.entity.basic;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;

import com.google.common.collect.ImmutableList;

/**
 * @deprecated since 0.6; code is unused and unnecessary
 */
public class EntityReferences {

    /**
     * Serialization helper.
     *
     * This masks (with transience) a remote entity (e.g a child or parent) during serialization,
     * by keeping a non-transient reference to the entity which owns the reference, 
     * and using his management context reference to find the referred Entity (master instance or proxy),
     * which is then cached.
     */
    public static class EntityReference<T extends Entity> implements Serializable {
        /**
         * 
         */
        private static final long serialVersionUID = -3464300667445096571L;

        protected Entity referrer;

        String id;
        transient T entity = null;

        public EntityReference(Entity referrer, String id) {
            this.referrer = referrer;
            this.id = id;
        }

        public EntityReference(Entity referrer, T reference) {
            this(referrer, reference.getId());
            entity = reference;
        }
        
        public T get() {
            T e = entity;
            if (e!=null) return e;
            return find();
        }

        public Entity getReferrer() {
            return referrer;
        }
        
        @SuppressWarnings("unchecked")
        protected synchronized T find() {
            if (entity != null) return entity;
            if (referrer == null)
                throw new IllegalStateException("EntityReference "+id+" should have been initialised with a reference parent");
            entity = (T) ((EntityInternal)referrer).getManagementContext().getEntityManager().getEntity(id);
            return entity;
        }
        
        synchronized void invalidate() {
            entity = null;
        }
        
        public String toString() {
            return getClass().getSimpleName()+"["+get().toString()+"]";
        }
    }


    public static class SelfEntityReference<T extends Entity> extends EntityReference<T> {
        private static final long serialVersionUID = 1594197133246032704L;
        
        private final T self;
        public SelfEntityReference(T self) {
            super(self, self);
            this.self = self;
        }
        protected synchronized T find() {
            return self;
        }
    }

    public static class EntityCollectionReference<T extends Entity> implements Serializable {
        private static final long serialVersionUID = 6923669408483258197L;
        private static final Logger LOG = LoggerFactory.getLogger(EntityCollectionReference.class);
        protected Entity referrer;
        
        Collection<String> entityRefs = new LinkedHashSet<String>();
        transient Map<String,T> entities = null;
        
        public EntityCollectionReference(Entity referrer) {
            this.referrer = referrer;
        }

        public Entity getReferrer() {
            return referrer;
        }
        
        public synchronized boolean add(T e) {
            if (entityRefs.add(e.getId())) {
                Map<String,T> e2 = new LinkedHashMap<String,T>(entities!=null?entities:Collections.<String,T>emptyMap());
                e2.put(e.getId(), e);
                entities = e2;
                return true;
            } else {
                return false;
            }
        }
        
        public synchronized void invalidate() {
            entities = null;
        }

        public synchronized boolean remove(Entity e) {
            if (entityRefs.remove(e.getId()) && entities!=null) {
                Map<String,T> e2 = new LinkedHashMap<String,T>(entities);
                e2.remove(e.getId());
                entities = e2;
                return true;
            } else {
                return false;
            }
        }

        /** returns the entity knwon here with the given id, if there is one, null otherwise;
         * makes no attempt to resolve */
        public synchronized Entity peek(String entityId) {
            if (entities!=null) return entities.get(entityId);
            return null;
        }

        public synchronized Collection<String> getIds() { return new ArrayList<String>(entityRefs); }
        
        public synchronized Collection<T> get() {
            Collection<T> result = entities==null ? null : entities.values();
            if (result==null) {
                result = find();
            }
            return ImmutableList.copyOf(result);
        }

        public synchronized void clear() {
            entityRefs.clear();
            entities = null;
        }
        
        public synchronized int size() {
            return entityRefs.size();
        }

        public synchronized boolean contains(Entity e) {
            return entityRefs.contains(e.getId());
        }

        @SuppressWarnings("unchecked")
        protected synchronized Collection<T> find() {
            if (entities!=null) return entities.values();
            if (referrer == null)
                throw new IllegalStateException("EntityReference should have been initialised with a reference parent");
            Map<String,T> result = new LinkedHashMap<String,T>();
            for (String it : entityRefs) {
                Entity e = ((EntityInternal)referrer).getManagementContext().getEntityManager().getEntity(it); 
                if (e==null) { 
                    LOG.warn("unable to find {}, referred to by {}", it, referrer);
                } else {
                    result.put(e.getId(), (T)e);
                }
            }
            entities = result;
            return entities.values();
        }
    }
}
