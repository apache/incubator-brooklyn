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
package org.apache.brooklyn.rest.util.json;

import java.io.IOException;
import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.objs.BrooklynObject;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class BidiSerialization {

    protected final static ThreadLocal<Boolean> STRICT_SERIALIZATION = new ThreadLocal<Boolean>(); 

    /**
     * Sets strict serialization on, or off (the default), for the current thread.
     * Recommended to be used in a <code>try { ... } finally { ... }</code> block
     * with {@link #clearStrictSerialization()} at the end.
     * <p>
     * With strict serialization, classes must have public fields or annotated fields, else they will not be serialized.
     */
    public static void setStrictSerialization(Boolean value) {
        STRICT_SERIALIZATION.set(value);
    }

    public static void clearStrictSerialization() {
        STRICT_SERIALIZATION.remove();
    }

    public static boolean isStrictSerialization() {
        Boolean result = STRICT_SERIALIZATION.get();
        if (result!=null) return result;
        return false;
    }


    public abstract static class AbstractWithManagementContextSerialization<T> {

        protected class Serializer extends JsonSerializer<T> {
            @Override
            public void serialize(T value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
                AbstractWithManagementContextSerialization.this.serialize(value, jgen, provider);
            }
        }
        
        protected class Deserializer extends JsonDeserializer<T> {
            @Override
            public T deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
                return AbstractWithManagementContextSerialization.this.deserialize(jp, ctxt);
            }
        }
        
        protected final Serializer serializer = new Serializer();
        protected final Deserializer deserializer = new Deserializer();
        protected final Class<T> type;
        protected final ManagementContext mgmt;
        
        public AbstractWithManagementContextSerialization(Class<T> type, ManagementContext mgmt) {
            this.type = type;
            this.mgmt = mgmt;
        }
        
        public JsonSerializer<T> getSerializer() {
            return serializer;
        }
        
        public JsonDeserializer<T> getDeserializer() {
            return deserializer;
        }

        public void serialize(T value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeStartObject();
            writeBody(value, jgen, provider);
            jgen.writeEndObject();
        }

        protected void writeBody(T value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeStringField("type", value.getClass().getCanonicalName());
            customWriteBody(value, jgen, provider);
        }

        public abstract void customWriteBody(T value, JsonGenerator jgen, SerializerProvider provider) throws IOException;

        public T deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            @SuppressWarnings("unchecked")
            Map<Object,Object> values = jp.readValueAs(Map.class);
            String type = (String) values.get("type");
            return customReadBody(type, values, jp, ctxt);
        }

        protected abstract T customReadBody(String type, Map<Object, Object> values, JsonParser jp, DeserializationContext ctxt) throws IOException;

        public void install(SimpleModule module) {
            module.addSerializer(type, serializer);
            module.addDeserializer(type, deserializer);
        }
    }
    
    public static class ManagementContextSerialization extends AbstractWithManagementContextSerialization<ManagementContext> {
        public ManagementContextSerialization(ManagementContext mgmt) { super(ManagementContext.class, mgmt); }
        @Override
        public void customWriteBody(ManagementContext value, JsonGenerator jgen, SerializerProvider provider) throws IOException {}
        @Override
        protected ManagementContext customReadBody(String type, Map<Object, Object> values, JsonParser jp, DeserializationContext ctxt) throws IOException {
            return mgmt;
        }
    }
    
    public abstract static class AbstractBrooklynObjectSerialization<T extends BrooklynObject> extends AbstractWithManagementContextSerialization<T> {
        public AbstractBrooklynObjectSerialization(Class<T> type, ManagementContext mgmt) { 
            super(type, mgmt);
        }
        @Override
        protected void writeBody(T value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeStringField("type", type.getCanonicalName());
            customWriteBody(value, jgen, provider);
        }
        @Override
        public void customWriteBody(T value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeStringField("id", value.getId());
        }
        @Override
        protected T customReadBody(String type, Map<Object, Object> values, JsonParser jp, DeserializationContext ctxt) throws IOException {
            return getInstanceFromId((String) values.get("id"));
        }
        protected abstract T getInstanceFromId(String id);
    }

    public static class EntitySerialization extends AbstractBrooklynObjectSerialization<Entity> {
        public EntitySerialization(ManagementContext mgmt) { super(Entity.class, mgmt); }
        @Override protected Entity getInstanceFromId(String id) { return mgmt.getEntityManager().getEntity(id); }
    }
    public static class LocationSerialization extends AbstractBrooklynObjectSerialization<Location> {
        public LocationSerialization(ManagementContext mgmt) { super(Location.class, mgmt); }
        @Override protected Location getInstanceFromId(String id) { return mgmt.getLocationManager().getLocation(id); }
    }
    // TODO how to look up policies and enrichers? (not essential...)
//    public static class PolicySerialization extends AbstractBrooklynObjectSerialization<Policy> {
//        public EntitySerialization(ManagementContext mgmt) { super(Policy.class, mgmt); }
//        @Override protected Policy getKind(String id) { return mgmt.getEntityManager().getEntity(id); }
//    }
//    public static class EnricherSerialization extends AbstractBrooklynObjectSerialization<Enricher> {
//        public EntitySerialization(ManagementContext mgmt) { super(Entity.class, mgmt); }
//        @Override protected Enricher getKind(String id) { return mgmt.getEntityManager().getEntity(id); }
//    }

}
