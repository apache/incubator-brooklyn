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

import org.apache.brooklyn.util.exceptions.Exceptions;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.JsonStreamContext;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.SerializerFactory;
import org.codehaus.jackson.map.ser.StdSerializerProvider;
import org.codehaus.jackson.type.JavaType;

/** allows the serializer-of-last-resort to be customized, ie used for unknown-types */
final class ConfigurableSerializerProvider extends StdSerializerProvider {
    
    public ConfigurableSerializerProvider() {}
    
    public ConfigurableSerializerProvider(SerializationConfig config) {
        // NB: not usually necessary to pass config, as object mapper gets its own config set explicitly
        this(config, new ConfigurableSerializerProvider(), null);
    }
    
    public ConfigurableSerializerProvider(SerializationConfig config, ConfigurableSerializerProvider src, SerializerFactory jsf) {
        super(config, src, jsf);
        unknownTypeSerializer = src.unknownTypeSerializer;
    }
    
    protected StdSerializerProvider createInstance(SerializationConfig config, SerializerFactory jsf) {
        return new ConfigurableSerializerProvider(config, this, jsf);
    }

    protected JsonSerializer<Object> unknownTypeSerializer;
    
    public JsonSerializer<Object> getUnknownTypeSerializer(Class<?> unknownType) {
        if (unknownTypeSerializer!=null) return unknownTypeSerializer;
        return super.getUnknownTypeSerializer(unknownType);
    }
    
    public void setUnknownTypeSerializer(JsonSerializer<Object> unknownTypeSerializer) {
        this.unknownTypeSerializer = unknownTypeSerializer;
    }

    @Override
    protected void _serializeValue(JsonGenerator jgen, Object value) throws IOException, JsonProcessingException {
        JsonStreamContext ctxt = jgen.getOutputContext();
        try {
            super._serializeValue(jgen, value);
        } catch (Exception e) {
            onSerializationException(ctxt, jgen, value, e);
        }
    }

    @Override
    protected void _serializeValue(JsonGenerator jgen, Object value, JavaType rootType) throws IOException, JsonProcessingException {
        JsonStreamContext ctxt = jgen.getOutputContext();
        try {
            super._serializeValue(jgen, value, rootType);
        } catch (Exception e) {
            onSerializationException(ctxt, jgen, value, e);
        }
    }

    protected void onSerializationException(JsonStreamContext ctxt, JsonGenerator jgen, Object value, Exception e) throws IOException, JsonProcessingException {
        Exceptions.propagateIfFatal(e);

        JsonSerializer<Object> unknownTypeSerializer = getUnknownTypeSerializer(value.getClass());
        if (unknownTypeSerializer instanceof ErrorAndToStringUnknownTypeSerializer) {
            ((ErrorAndToStringUnknownTypeSerializer)unknownTypeSerializer).serializeFromError(ctxt, e, value, jgen, this);
        } else {
            unknownTypeSerializer.serialize(value, jgen, this);
        }
    }
}
