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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.DefaultSerializerProvider;
import com.fasterxml.jackson.databind.ser.SerializerFactory;

/** allows the serializer-of-last-resort to be customized, ie used for unknown-types */
final class ConfigurableSerializerProvider extends DefaultSerializerProvider {

    protected JsonSerializer<Object> unknownTypeSerializer;

    public ConfigurableSerializerProvider() {}

    @Override
    public DefaultSerializerProvider createInstance(SerializationConfig config, SerializerFactory jsf) {
        return new ConfigurableSerializerProvider(config, this, jsf);
    }

    public ConfigurableSerializerProvider(SerializationConfig config, ConfigurableSerializerProvider src, SerializerFactory jsf) {
        super(src, config, jsf);
        unknownTypeSerializer = src.unknownTypeSerializer;
    }

    public JsonSerializer<Object> getUnknownTypeSerializer(Class<?> unknownType) {
        if (unknownTypeSerializer!=null) return unknownTypeSerializer;
        return super.getUnknownTypeSerializer(unknownType);
    }

    public void setUnknownTypeSerializer(JsonSerializer<Object> unknownTypeSerializer) {
        this.unknownTypeSerializer = unknownTypeSerializer;
    }

    @Override
    public void serializeValue(JsonGenerator jgen, Object value) throws IOException {
        JsonStreamContext ctxt = jgen.getOutputContext();
        try {
            super.serializeValue(jgen, value);
        } catch (Exception e) {
            onSerializationException(ctxt, jgen, value, e);
        }
    }

    @Override
    public void serializeValue(JsonGenerator jgen, Object value, JavaType rootType) throws IOException {
        JsonStreamContext ctxt = jgen.getOutputContext();
        try {
            super.serializeValue(jgen, value, rootType);
        } catch (Exception e) {
            onSerializationException(ctxt, jgen, value, e);
        }
    }

    protected void onSerializationException(JsonStreamContext ctxt, JsonGenerator jgen, Object value, Exception e) throws IOException {
        Exceptions.propagateIfFatal(e);

        JsonSerializer<Object> unknownTypeSerializer = getUnknownTypeSerializer(value.getClass());
        if (unknownTypeSerializer instanceof ErrorAndToStringUnknownTypeSerializer) {
            ((ErrorAndToStringUnknownTypeSerializer)unknownTypeSerializer).serializeFromError(ctxt, e, value, jgen, this);
        } else {
            unknownTypeSerializer.serialize(value, jgen, this);
        }
    }
}
