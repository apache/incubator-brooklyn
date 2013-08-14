package brooklyn.entity.basic;

import java.util.Collection;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.management.ExecutionManager;
import brooklyn.management.Task;
import brooklyn.util.task.Tasks;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

/** Provides utilities for making Tasks easier to work with in Brooklyn.
 * Main thing at present is to supply (and find) wrapped entities for tasks to understand the
 * relationship of the entity to the task.
 * TODO Longer term it would be better to remove 'tags' on Tasks and use a strongly typed context object.
 * (Tags there are used mainly for determining who called it (caller), what they called it on (target entity),
 * and what type of task it is (effector, schedule/sensor, etc).)
 */
public class BrooklynTasks {

    private static final Logger log = LoggerFactory.getLogger(BrooklynTasks.WrappedEntity.class);
    
    public static class WrappedEntity {
        public final String wrappingType;
        public final Entity entity;
        public WrappedEntity(String wrappingType, Entity entity) {
            Preconditions.checkNotNull(wrappingType);
            Preconditions.checkNotNull(entity);
            this.wrappingType = wrappingType;
            this.entity = entity;
        }
        @Override
        public String toString() {
            return "Wrapped["+wrappingType+":"+entity+"]";
        }
        @Override
        public int hashCode() {
            return Objects.hashCode(entity, wrappingType);
        }
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof WrappedEntity)) return false;
            return 
                Objects.equal(entity, ((WrappedEntity)obj).entity) &&
                Objects.equal(wrappingType, ((WrappedEntity)obj).wrappingType);
        }
    }
    
    public static final String CONTEXT_ENTITY = "contextEntity";
    public static final String CALLER_ENTITY = "callerEntity";
    public static final String TARGET_ENTITY = "targetEntity";
    
    public static WrappedEntity tagForContextEntity(Entity entity) {
        return new WrappedEntity(CONTEXT_ENTITY, entity);
    }
    
    public static WrappedEntity tagForCallerEntity(Entity entity) {
        return new WrappedEntity(CALLER_ENTITY, entity);
    }
    
    public static WrappedEntity tagForTargetEntity(Entity entity) {
        return new WrappedEntity(TARGET_ENTITY, entity);
    }

    public static Entity getWrappedEntityOfType(Task<?> t, String wrappingType) {
        return getWrappedEntityOfType(t.getTags(), wrappingType);
    }
    public static Entity getWrappedEntityOfType(Collection<?> tags, String wrappingType) {
        for (Object x: tags)
            if ((x instanceof WrappedEntity) && ((WrappedEntity)x).wrappingType.equals(wrappingType))
                return ((WrappedEntity)x).entity;
        return null;
    }

    public static Entity getContextEntity(Task<?> task) {
        return getWrappedEntityOfType(task, CONTEXT_ENTITY);
    }

    public static Entity getTargetOrContextEntity(Task<?> t) {
        Entity result = getWrappedEntityOfType(t, TARGET_ENTITY);
        if (result!=null) return result;
        result = getWrappedEntityOfType(t, CONTEXT_ENTITY);
        if (result!=null) return result;
        
        result = Tasks.tag(t, Entity.class, false);
        if (result!=null) {
            log.warn("Context entity found by looking at 'Entity' tag, not wrapped entity");
        }
        return result;
    }
    
    public static Set<Task<?>> getTasksInEntityContext(ExecutionManager em, Entity e) {
        return em.getTasksWithTag(tagForContextEntity(e));
    }

}
