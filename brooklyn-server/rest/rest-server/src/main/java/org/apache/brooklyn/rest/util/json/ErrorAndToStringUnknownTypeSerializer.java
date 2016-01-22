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
import java.io.NotSerializableException;
import java.util.Collections;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.javalang.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.impl.UnknownSerializer;

/**
 * for non-json-serializable classes (quite a lot of them!) simply provide a sensible error message and a toString.
 * TODO maybe we want to attempt to serialize fields instead?  (but being careful not to be self-referential!)
 */
public class ErrorAndToStringUnknownTypeSerializer extends UnknownSerializer {

    private static final Logger log = LoggerFactory.getLogger(ErrorAndToStringUnknownTypeSerializer.class);
    private static Set<String> WARNED_CLASSES = Collections.synchronizedSet(MutableSet.<String>of());

    @Override
    public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        if (BidiSerialization.isStrictSerialization())
            throw new JsonMappingException("Cannot serialize object containing "+value.getClass().getName()+" when strict serialization requested");

        serializeFromError(jgen.getOutputContext(), null, value, jgen, provider);
    }

    public void serializeFromError(JsonStreamContext ctxt, @Nullable Exception error, Object value, JsonGenerator jgen, SerializerProvider configurableSerializerProvider) throws IOException {
        if (log.isDebugEnabled())
            log.debug("Recovering from json serialization error, serializing "+value+": "+error);

        if (BidiSerialization.isStrictSerialization())
            throw new JsonMappingException("Cannot serialize "
                + (ctxt!=null && !ctxt.inRoot() ? "object containing " : "")
                + value.getClass().getName()+" when strict serialization requested");

        if (WARNED_CLASSES.add(value.getClass().getCanonicalName())) {
            log.warn("Standard serialization not possible for "+value.getClass()+" ("+value+")", error);
        }
        JsonStreamContext newCtxt = jgen.getOutputContext();

        // very odd, but flush seems necessary when working with large objects; presumably a buffer which is allowed to clear itself?
        // without this, when serializing the large (1.5M) Server json object from BrooklynJacksonSerializerTest creates invalid json,
        // containing:  "foo":false,"{"error":true,...
        jgen.flush();

        boolean createObject = !newCtxt.inObject() || newCtxt.getCurrentName()!=null;
        if (createObject) {
            jgen.writeStartObject();
        }

        if (allowEmpty(value.getClass())) {
            // write nothing
        } else {

            jgen.writeFieldName("error");
            jgen.writeBoolean(true);

            jgen.writeFieldName("errorType");
            jgen.writeString(NotSerializableException.class.getCanonicalName());

            jgen.writeFieldName("type");
            jgen.writeString(value.getClass().getCanonicalName());

            jgen.writeFieldName("toString");
            jgen.writeString(value.toString());

            if (error!=null) {
                jgen.writeFieldName("causedByError");
                jgen.writeString(error.toString());
            }

        }

        if (createObject) {
            jgen.writeEndObject();
        }

        while (newCtxt!=null && !newCtxt.equals(ctxt)) {
            if (jgen.getOutputContext().inArray()) { jgen.writeEndArray(); continue; }
            if (jgen.getOutputContext().inObject()) { jgen.writeEndObject(); continue; }
            break;
        }

    }

    protected boolean allowEmpty(Class<? extends Object> clazz) {
        if (clazz.getAnnotation(JsonSerialize.class)!=null && Reflections.hasNoNonObjectFields(clazz)) {
            return true;
        } else {
            return false;
        }
    }
}
