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
package org.apache.brooklyn.util.core.xstream;

import java.util.Map;
import java.util.Map.Entry;

import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Identifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;

/** converter which simplifies representation of a map for string-based keys,
 * to <key>value</key>, or <entry key="key" type="string">value</entry> 
 * @author alex
 *
 */
public class StringKeyMapConverter extends MapConverter {

    private static final Logger log = LoggerFactory.getLogger(StringKeyMapConverter.class);
    
    // full stop is technically allowed ... goes against "best practice" ... 
    // but simplifies property maps, and is used elsewhere in xstream's repn
    final static String VALID_XML_NODE_NAME_CHARS = Identifiers.JAVA_GOOD_NONSTART_CHARS + ".";

    final static String VALID_XML_NODE_NAME_START_CHARS = Identifiers.JAVA_GOOD_START_CHARS + ".";

    public StringKeyMapConverter(Mapper mapper) {
        super(mapper);
    }
    
    protected boolean isKeyValidForNodeName(String key) {
        // return false to always write as <entry key="key" ...; otherwise only use that when key is not valid xml
        return Identifiers.isValidToken(key, VALID_XML_NODE_NAME_START_CHARS, VALID_XML_NODE_NAME_CHARS);
    }
    
    public boolean canConvert(Class type) {
        return super.canConvert(type) || type.getName().equals(MutableMap.class.getName());
    }
    
    @Override
    protected void marshalEntry(HierarchicalStreamWriter writer, MarshallingContext context, Entry entry) {
        if (entry.getKey() instanceof String) {
            marshalStringKey(writer, context, entry);
        } else {
            super.marshalEntry(writer, context, entry);
        }
    }
    
    protected void marshalStringKey(HierarchicalStreamWriter writer, MarshallingContext context, Entry entry) {
        String key = (String)entry.getKey();
        String entryNodeName = getEntryNodeName();
        boolean useKeyAsNodeName = (!key.equals(entryNodeName) && isKeyValidForNodeName(key));
        if (useKeyAsNodeName) entryNodeName = key;
        ExtendedHierarchicalStreamWriterHelper.startNode(writer, entryNodeName, Map.Entry.class);
        if (!useKeyAsNodeName)
            writer.addAttribute("key", key);
        
        Object value = entry.getValue();
        if (entry.getValue()!=null && isInlineableType(value.getClass())) {
            if (!(value instanceof String))
                writer.addAttribute("type", mapper().serializedClass(entry.getValue().getClass()));
            if (entry.getValue().getClass().isEnum())
                writer.setValue(((Enum)entry.getValue()).name());
            else
                writer.setValue(""+entry.getValue());
        } else {
            writeItem(entry.getValue(), context, writer);
        }
        
        writer.endNode();
    }

    protected boolean isInlineableType(Class<?> type) {
        return TypeCoercions.isPrimitiveOrBoxer(type) || String.class.equals(type) || type.isEnum();
    }
    
    @Override
    protected void unmarshalEntry(HierarchicalStreamReader reader, UnmarshallingContext context, Map map) {
        String key = reader.getNodeName(); 
        if (key.equals(getEntryNodeName())) key = reader.getAttribute("key");
        if (key==null) {
            super.unmarshalEntry(reader, context, map);
        } else {
            unmarshalStringKey(reader, context, map, key);
        }
    }

    protected void unmarshalStringKey(HierarchicalStreamReader reader, UnmarshallingContext context, Map map, String key) {
        String type = reader.getAttribute("type");
        Object value;
        if (type==null && reader.hasMoreChildren()) {
            reader.moveDown();
            value = readItem(reader, context, map);
            reader.moveUp();
        } else {
            Class typeC = type!=null ? mapper().realClass(type) : String.class;
            try {
                value = TypeCoercions.coerce(reader.getValue(), typeC);
            } catch (Exception e) {
                log.warn("FAILED to coerce "+reader.getValue()+" to "+typeC+": "+e);
                throw Exceptions.propagate(e);
            }
        }
        map.put(key, value);
    }

}
