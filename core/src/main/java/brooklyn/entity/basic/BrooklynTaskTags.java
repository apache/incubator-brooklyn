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
package brooklyn.entity.basic;

import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.management.ExecutionManager;
import brooklyn.management.Task;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.TaskTags;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;
import brooklyn.util.text.StringEscapes.BashStringEscapes;

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
public class BrooklynTaskTags extends TaskTags {

    private static final Logger log = LoggerFactory.getLogger(BrooklynTaskTags.WrappedEntity.class);

    public static final String EFFECTOR_TAG = "EFFECTOR";
    public static final String NON_TRANSIENT_TASK_TAG = "NON-TRANSIENT";
    /** indicates a task is transient, roughly that is to say it is uninteresting -- 
     * specifically this means it can be GC'd as soon as it is completed, 
     * and that it need not appear in some task lists;
     * often used for framework lifecycle events and sensor polling */
    public static final String TRANSIENT_TASK_TAG = "TRANSIENT";

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
        if (t==null) return null;
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
        if (t==null) return null;
        Entity result = getWrappedEntityOfType(t, CONTEXT_ENTITY);
        if (result!=null) return result;
        result = getWrappedEntityOfType(t, TARGET_ENTITY);
        if (result!=null) {
            log.warn("Context entity found by looking at target entity tag, not context entity");
            return result;
        }
        
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
            this.streamSize = streamSize != null ? streamSize : Suppliers.<Integer>ofInstance(streamContents.get().length());
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
    /** not a stream, but inserted with the same mechanism */
    public static final String STREAM_ENV = "env";
    
    /** creates a tag suitable for marking a stream available on a task */
    public static WrappedStream tagForStream(String streamType, ByteArrayOutputStream stream) {
        return new WrappedStream(streamType, stream);
    }

    /** creates a tag suitable for marking a stream available on a task */
    public static WrappedStream tagForStream(String streamType, Supplier<String> contents, Supplier<Integer> size) {
        return new WrappedStream(streamType, contents, size);
    }
    
    /** creates a tag suitable for attaching a snapshot of an environment var map as a "stream" on a task;
     * mainly for use with STREAM_ENV */ 
    public static WrappedStream tagForEnvStream(String streamEnv, Map<?, ?> env) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<?,?> kv: env.entrySet()) {
            Object val = kv.getValue();
            sb.append(kv.getKey()+"=" +
                (val!=null ? BashStringEscapes.wrapBash(val.toString()) : "") + "\n");
        }
        return BrooklynTaskTags.tagForStream(BrooklynTaskTags.STREAM_ENV, Streams.byteArrayOfString(sb.toString()));
    }

    /** returns the set of tags indicating the streams available on a task */
    public static Set<WrappedStream> streams(Task<?> task) {
        Set<WrappedStream> result = new LinkedHashSet<BrooklynTaskTags.WrappedStream>();
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

    // ------ misc
    
    public static void setTransient(Task<?> task) {
        addTagDynamically(task, TRANSIENT_TASK_TAG);
    }
    
    public static void setInessential(Task<?> task) {
        addTagDynamically(task, INESSENTIAL_TASK);
    }

    // ------ effector tags
    
    public static String tagForEffectorName(String name) {
        return EFFECTOR_TAG+":"+name;
    }
    
    /**
     * checks if the given task is part of the given effector call on the given entity;
     * @param task  the task to check (false if null)
     * @param entity  the entity where this effector task should be running, or any entity if null
     * @param effector  the effector (matching name) where this task should be running, or any effector if null
     * @param allowNestedEffectorCalls  whether to match ancestor effector calls, e.g. if eff1 calls eff2,
     *   and we are checking eff2, whether to match eff1
     * @return whether the given task is part of the given effector
     */
    public static boolean isInEffectorTask(Task<?> task, Entity entity, Effector<?> effector, boolean allowNestedEffectorCalls) {
        Task<?> t = task;
        while (t!=null) {
            Set<Object> tags = t.getTags();
            if (tags.contains(EFFECTOR_TAG)) {
                boolean match = true;
                if (entity!=null && !entity.equals(getTargetOrContextEntity(t)))
                    match = false;
                if (effector!=null && !tags.contains(tagForEffectorName(effector.getName())))
                    match = false;
                if (match) return true;
                if (!allowNestedEffectorCalls) return false;
            }
            t = t.getSubmittedByTask();
        }
        return false;
    }
    
    public static String getEffectorName(Task<?> task) {
        Task<?> t = task;
        while (t!=null) {
            for (Object tag: task.getTags()) {
                if (tag instanceof String && ((String)tag).startsWith(EFFECTOR_TAG+":"))
                    return Strings.removeFromStart((String)tag, EFFECTOR_TAG+":");
            }
            t = t.getSubmittedByTask();
        }
        return null;
    }

}
