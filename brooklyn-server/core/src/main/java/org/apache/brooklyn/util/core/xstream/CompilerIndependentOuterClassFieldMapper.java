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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thoughtworks.xstream.core.Caching;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.MapperWrapper;

/**
 * <p>Compiler independent outer class field mapper.</p>
 * <p>Different compilers generate different indexes for the names of outer class reference
 *    field (this$N) leading to deserialization errors.</p>
 * <ul>
 *   <li> eclipse-[groovy-]compiler counts all outer static classes
 *   <li> OpenJDK/Oracle/IBM compiler starts at 0, regardless of the nesting level
 * </ul>
 * <p>The mapper will be able to update field names for instances with a single this$N
 *    field only (including those from parent classes).</p>
 * <p>For difference between generated field names compare
 *    {@code src/test/java/brooklyn/util/xstream/compiler_compatibility_eclipse.xml} and
 *    {@code src/test/java/brooklyn/util/xstream/compiler_compatibility_oracle.xml},
 *    generated from {@code org.apache.brooklyn.core.util.xstream.CompilerCompatibilityTest}</p>
 * <p>JLS 1.1 relevant section, copied verbatim for a lack of reliable URL:</p>
 * <blockquote>
 *  <p>Java 1.1 compilers are strongly encouraged, though not required, to use the
 *     following naming conventions when implementing inner classes. Compilers may
 *     not use synthetic names of the forms defined here for any other purposes.</p>
 *  <p>A synthetic field pointing to the outermost enclosing instance is named this$0.
 *     The next-outermost enclosing instance is this$1, and so forth. (At most one such
 *     field is necessary in any given inner class.) A synthetic field containing a copy
 *     of a constant v is named val$v. These fields are final.</p>
 * </blockquote>
 * <p>Currently available at
 *    http://web.archive.org/web/20000830111107/http://java.sun.com/products/jdk/1.1/docs/guide/innerclasses/spec/innerclasses.doc10.html</p>
 */
public class CompilerIndependentOuterClassFieldMapper extends MapperWrapper implements Caching {
    public static final Logger LOG = LoggerFactory.getLogger(CompilerIndependentOuterClassFieldMapper.class);

    private static final String OUTER_CLASS_FIELD_PREFIX = "this$";

    private final Map<String, Collection<String>> classOuterFields = new ConcurrentHashMap<String, Collection<String>>();

    public CompilerIndependentOuterClassFieldMapper(Mapper wrapped) {
        super(wrapped);
        classOuterFields.put(Object.class.getName(), Collections.<String>emptyList());
    }

    @Override
    public String realMember(@SuppressWarnings("rawtypes") Class type, String serialized) {
        // Let com.thoughtworks.xstream.mapper.OuterClassMapper also run on the input.
        String serializedFieldName = super.realMember(type, serialized);

        if (serializedFieldName.startsWith(OUTER_CLASS_FIELD_PREFIX)) {
            Collection<String> compiledFieldNames = findOuterClassFieldNames(type);
            if (compiledFieldNames.size() == 0) {
                throw new IllegalStateException("Unable to find any outer class fields in " + type + ", searching specifically for " + serializedFieldName);
            }

            Set<String> uniqueFieldNames = new HashSet<String>(compiledFieldNames);
            String deserializeFieldName;
            if (!compiledFieldNames.contains(serializedFieldName)) {
                String msg =
                        "Unable to find outer class field " + serializedFieldName + " in class " + type + ". " +
                        "This could be caused by " +
                        "1) changing the class (or one of its parents) to a static or " +
                        "2) moving the class to a different lexical level (enclosing classes) or " +
                        "3) using a different compiler (i.e eclipse vs oracle) at the time the object was serialized. ";
                if (uniqueFieldNames.size() == 1) {
                    // Try to fix the field naming only for the case with a single field or 
                    // multiple fields with the same name, in which case XStream puts defined-in
                    // for the field declared in super.
                    //
                    // We don't have access to the XML elements from here to check for same name
                    // so we check the target class instead. This should work most of the time, but
                    // if code is recompiled in such a way that the new instance has fields with
                    // different names, where only the field of the extending class is renamed and
                    // the super field is not, then the instance will be deserialized incorrectly -
                    // the super field will be assigned both times. If the field type is incompatible
                    // then a casting exception will be thrown, if it's the same then only the warning
                    // below will indicate of a possible problem down the line - most probably NPE on
                    // the this$N field.
                    deserializeFieldName = compiledFieldNames.iterator().next();
                    LOG.warn(msg + "Will use the field " + deserializeFieldName + " instead.");
                } else {
                    // Multiple fields with differing names case - don't try to fix it.
                    // Better fail with an explicit error, and have someone fix it manually,
                    // than try to fix it here non-reliably and have it fail down the line
                    // with some unrelated error.
                    // XStream will fail later with a field not found exception.
                    LOG.error(msg + "Will fail with a field not found exception. " +
                            "Edit the persistence state manually and update the field names. "+
                            "Existing field names are " + uniqueFieldNames);
                    deserializeFieldName = serializedFieldName;
                }
            } else {
                if (uniqueFieldNames.size() > 1) {
                    // Log at debug level as the actual problem would occur in very specific cases. Only
                    // useful when the compiler is changed, otherwise leads to false positives.
                    LOG.debug("Deserializing the non-static class " + type + " with multiple outer class fields " + uniqueFieldNames + ". " +
                            "When changing compilers it's possible that the instance won't be able to be deserialized due to changed outer class field names. " +
                            "In those cases deserialization could fail with field not found exception or class cast exception following this log line.");
                }
                deserializeFieldName = serializedFieldName;
            }

            return deserializeFieldName;
        } else {
            return serializedFieldName;
        }
    }
    
    private Collection<String> findOuterClassFieldNames(Class<?> type) {
        Collection<String> fields = classOuterFields.get(type.getName());
        if (fields == null) {
            fields = new ArrayList<String>();
            addOuterClassFields(type, fields);
            classOuterFields.put(type.getName(), fields);
        }
        return fields;
    }
    
    private void addOuterClassFields(Class<?> type, Collection<String> fields) {
        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic()) {
                fields.add(field.getName());
            }
        }
        if (type.getSuperclass() != null) {
            addOuterClassFields(type.getSuperclass(), fields);
        }
    }

    @Override
    public void flushCache() {
        classOuterFields.keySet().retainAll(Collections.singletonList(Object.class.getName()));
    }

}