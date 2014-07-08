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
package brooklyn.util.xstream;

import java.util.Map;
import java.util.Map.Entry;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.mapper.Mapper;

public class ImmutableMapConverter extends MapConverter {

    public ImmutableMapConverter(Mapper mapper) {
        super(mapper);
    }

    @Override
    public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
        return ImmutableMap.class.isAssignableFrom(type);
    }

    // marshalling is the same
    // so is unmarshalling the entries

    // only differences are creating the overarching collection, which we do after the fact
    // (optimizing format on disk as opposed to in-memory), and we discard null key/values 
    // to avoid failing entirely.
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        Map<?, ?> map = Maps.newLinkedHashMap();
        populateMap(reader, context, map);
        return ImmutableMap.copyOf(Maps.filterEntries(map, new Predicate<Map.Entry<?,?>>() {
                @Override public boolean apply(Entry<?, ?> input) {
                    return input != null && input.getKey() != null && input.getValue() != null;
                }}));
    }
}
