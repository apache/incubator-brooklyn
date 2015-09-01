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

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.extended.JavaClassConverter;
import com.thoughtworks.xstream.mapper.DefaultMapper;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.MapperWrapper;

public class XmlSerializer<T> {

    private final Map<String, String> deserializingClassRenames;
    // XXX protected
    public final XStream xstream;

    public XmlSerializer() {
        this(ImmutableMap.<String, String>of());
    }
    
    public XmlSerializer(Map<String, String> deserializingClassRenames) {
        this.deserializingClassRenames = deserializingClassRenames;
        this.xstream = new XStream() {
            @Override
            protected MapperWrapper wrapMapper(MapperWrapper next) {
                return XmlSerializer.this.wrapMapperForNormalUsage( super.wrapMapper(next) );
            }
        };
        
        xstream.registerConverter(newCustomJavaClassConverter(), XStream.PRIORITY_NORMAL);
        
        // list as array list is default
        xstream.alias("map", Map.class, LinkedHashMap.class);
        xstream.alias("set", Set.class, LinkedHashSet.class);
        
        xstream.registerConverter(new StringKeyMapConverter(xstream.getMapper()), /* priority */ 10);
        xstream.alias("MutableMap", MutableMap.class);
        xstream.alias("MutableSet", MutableSet.class);
        xstream.alias("MutableList", MutableList.class);
        
        // Needs an explicit MutableSet converter!
        // Without it, the alias for "set" seems to interfere with the MutableSet.map field, so it gets
        // a null field on deserialization.
        xstream.registerConverter(new MutableSetConverter(xstream.getMapper()));
        
        xstream.aliasType("ImmutableList", ImmutableList.class);
        xstream.registerConverter(new ImmutableListConverter(xstream.getMapper()));
        xstream.registerConverter(new ImmutableSetConverter(xstream.getMapper()));
        xstream.registerConverter(new ImmutableMapConverter(xstream.getMapper()));

        xstream.registerConverter(new EnumCaseForgivingConverter());
        xstream.registerConverter(new Inet4AddressConverter());
    }

    /**
     * JCC is used when class names are serialized/deserialized and no alias is defined;
     * it is configured in XStream *without* access to the XStream mapper.
     * However we need a few selected mappers (see {@link #wrapMapperForAllLowLevelMentions(Mapper)} )
     * in order to effect renames at the low level, but many of the mappers must NOT be used,
     * e.g. because some might intercept all Class<? extends Entity> references
     * (and that interception is only wanted when serializing <i>instances</i>,
     * as in {@link #wrapMapperForNormalUsage(Mapper)}).
     * <p>
     * This can typically be done simply by registering our own instance (due to order guarantee of PrioritizedList),
     * after the instance added by XStream.setupConverters()
     */
    private JavaClassConverter newCustomJavaClassConverter() {
        return new JavaClassConverter(wrapMapperForAllLowLevelMentions(new DefaultMapper(xstream.getClassLoaderReference()))) {};
    }
    
    /** Adds mappers needed for *any* reference to a class, e.g. when names are used for inner classes, or classes are renamed;
     * this *excludes* basic mentions, however, because most rewrites should *not* be applied at this deep level;
     * mappers which effect aliases or intercept references to entities are usually NOT be invoked in this low-level pathway.
     * See {@link #newCustomJavaClassConverter()}. */
    protected MapperWrapper wrapMapperForAllLowLevelMentions(Mapper next) {
        MapperWrapper result = new CompilerIndependentOuterClassFieldMapper(next);
        return new ClassRenamingMapper(result, deserializingClassRenames);
    }
    /** Extension point where sub-classes can add mappers wanted when instances of a class are serialized, 
     * including {@link #wrapMapperForAllLowLevelMentions(Mapper)}, plus any usual domain mappings. */
    protected MapperWrapper wrapMapperForNormalUsage(Mapper next) {
        return wrapMapperForAllLowLevelMentions(next);
    }

    public void serialize(Object object, Writer writer) {
        xstream.toXML(object, writer);
    }

    @SuppressWarnings("unchecked")
    public T deserialize(Reader xml) {
        return (T) xstream.fromXML(xml);
    }

    public String toString(T memento) {
        Writer writer = new StringWriter();
        serialize(memento, writer);
        return writer.toString();
    }

    public T fromString(String xml) {
        return deserialize(new StringReader(xml));
    }

}
