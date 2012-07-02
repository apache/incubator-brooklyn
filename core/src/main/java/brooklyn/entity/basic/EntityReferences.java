package brooklyn.entity.basic;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;

import com.google.common.collect.ImmutableList;

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
        
        protected synchronized T find() {
            if (entity != null) return entity;
            if (referrer == null)
                throw new IllegalStateException("EntityReference "+id+" should have been initialised with a reference owner");
            entity = (T) ((AbstractEntity)referrer).getManagementContext().getEntity(id);
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
        transient Collection<T> entities = null;
        
        public EntityCollectionReference(Entity referrer) {
            this.referrer = referrer;
        }

        public Entity getReferrer() {
            return referrer;
        }
        
        public synchronized boolean add(T e) {
            if (entityRefs.add(e.getId())) {
                Collection<T> e2 = new LinkedHashSet<T>(entities!=null?entities:Collections.<T>emptySet());
                e2.add(e);
                entities = e2;
                return true;
            } else {
                return false;
            }
        }

        public synchronized boolean remove(Entity e) {
            if (entityRefs.remove(e.getId()) && entities!=null) {
                Collection<T> e2 = new LinkedHashSet<T>(entities);
                e2.remove(e);
                entities = e2;
                return true;
            } else {
                return false;
            }
        }

        public synchronized Collection<T> get() {
            Collection<T> result = entities;
            if (result==null) {
                result = find();
            }
            return ImmutableList.copyOf(result);
        }

        public synchronized int size() {
            return entityRefs.size();
        }

        public synchronized boolean contains(Entity e) {
            return entityRefs.contains(e.getId());
        }

        protected synchronized Collection<T> find() {
            if (entities!=null) return entities;
            if (referrer == null)
                throw new IllegalStateException("EntityReference should have been initialised with a reference owner");
            Collection<T> result = new CopyOnWriteArrayList<T>();
            for (String it : entityRefs) {
                Entity e = ((AbstractEntity)referrer).getManagementContext().getEntity(it); 
                if (e==null) { 
                    LOG.warn("unable to find {}, referred to by {}", it, referrer);
                } else {
                    result.add((T)e);
                }
            }
            entities = result;
            return entities;
        }
    }
}
