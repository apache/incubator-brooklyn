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
import java.util.Collection;
import java.util.Map;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.ser.std.SerializerBase;

import com.google.common.annotations.Beta;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

/**
 * Provides a serializer for {@link Multimap} instances.
 * <p>
 * When Brooklyn's Jackson dependency is updated from org.codehaus.jackson:1.9.13 to
 * com.fasterxml.jackson:2.3+ then this class should be replaced with a dependency on
 * jackson-datatype-guava and a GuavaModule registered with Brooklyn's ObjectMapper.
 */
@Beta
public class MultimapSerializer extends SerializerBase<Multimap<?, ?>> {

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected MultimapSerializer() {
        super((Class<Multimap<?, ?>>) (Class) Multimap.class);
    }

    @Override
    public void serialize(Multimap<?, ?> value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        writeEntries(value, jgen, provider);
        jgen.writeEndObject();
    }

    private void writeEntries(Multimap<?, ?> value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        for (Map.Entry<?, ? extends Collection<?>> entry : value.asMap().entrySet()) {
            provider.findKeySerializer(provider.constructType(String.class), null)
                    .serialize(entry.getKey(), jgen, provider);
            provider.defaultSerializeValue(Lists.newArrayList(entry.getValue()), jgen);
        }
    }
}
