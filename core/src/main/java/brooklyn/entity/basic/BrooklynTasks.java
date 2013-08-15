package brooklyn.entity.basic;

import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.management.ExecutionManager;
import brooklyn.management.Task;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;

/** Provides utilities for making Tasks easier to work with in Brooklyn.
 * Main thing at present is to supply (and find) wrapped entities for tasks to understand the
 * relationship of the entity to the task.
 * TODO Longer term it would be better to remove 'tags' on Tasks and use a strongly typed context object.
 * (Tags there are used mainly for determining who called it (caller), what they called it on (target entity),
 * and what type of task it is (effector, schedule/sensor, etc).)
 */
public class BrooklynTasks {

    private static final Logger log = LoggerFactory.getLogger(BrooklynTasks.WrappedEntity.class);

    // ------------- entity tags -------------------------
    
    public static class WrappedEntity {
        public final String wrappingType;
        public final Entity entity;
        protected WrappedEntity(String wrappingType, Entity entity) {
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

    // ------------- stream tags -------------------------

    public static class WrappedStream {
        public final String streamType;
        public final Supplier<String> streamContents;
        public final Supplier<Integer> streamSize;
        protected WrappedStream(String streamType, Supplier<String> streamContents, Supplier<Integer> streamSize) {
            Preconditions.checkNotNull(streamType);
            Preconditions.checkNotNull(streamContents);
            this.streamType = streamType;
            this.streamContents = streamContents;
            this.streamSize = streamSize != null ? streamSize : Suppliers.<Integer>ofInstance(null);
        }
        protected WrappedStream(String streamType, ByteArrayOutputStream stream) {
            Preconditions.checkNotNull(streamType);
            Preconditions.checkNotNull(stream);
            this.streamType = streamType;
            this.streamContents = Strings.toStringSupplier(stream);
            this.streamSize = Streams.sizeSupplier(stream);
        }
        @Override
        public String toString() {
            return "Stream["+streamType+"/"+Strings.makeSizeString(streamSize.get())+"]";
        }
        @Override
        public int hashCode() {
            return Objects.hashCode(streamContents, streamType);
        }
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof WrappedStream)) return false;
            return 
                Objects.equal(streamContents, ((WrappedStream)obj).streamContents) &&
                Objects.equal(streamType, ((WrappedStream)obj).streamType);
        }
    }
    
    public static final String STREAM_STDIN = "stdin";
    public static final String STREAM_STDOUT = "stdout";
    public static final String STREAM_STDERR = "stderr";
    
    /** creates a tag suitable for marking a stream available on a task */
    public static WrappedStream tagForStream(String streamType, ByteArrayOutputStream stream) {
        return new WrappedStream(streamType, stream);
    }

    /** returns the set of tags indicating the streams available on a task */
    public static Set<WrappedStream> streams(Task<?> task) {
        Set<WrappedStream> result = new LinkedHashSet<BrooklynTasks.WrappedStream>();
        for (Object tag: task.getTags()) {
            if (tag instanceof WrappedStream) {
                result.add((WrappedStream)tag);
            }
        }
        return ImmutableSet.copyOf(result);
    }

    /** returns the tag for the indicated stream, or null */
    public static WrappedStream stream(Task<?> task, String streamType) {
        for (Object tag: task.getTags())
            if ((tag instanceof WrappedStream) && ((WrappedStream)tag).streamType.equals(streamType))
                return (WrappedStream)tag;
        return null;
    }
}
