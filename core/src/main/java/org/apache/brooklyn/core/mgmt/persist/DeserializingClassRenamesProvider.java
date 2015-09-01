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
package org.apache.brooklyn.core.mgmt.persist;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;

import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.javalang.Reflections;
import org.apache.brooklyn.util.stream.Streams;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

@Beta
public class DeserializingClassRenamesProvider {

    public static final String DESERIALIZING_CLASS_RENAMES_PROPERTIES_PATH = "classpath://org/apache/brooklyn/core/mgmt/persist/deserializingClassRenames.properties";
    
    private static Map<String, String> cache = null;
    
    @Beta
    public static Map<String, String> loadDeserializingClassRenames() {
        if (cache!=null) return cache;
        synchronized (DeserializingClassRenamesProvider.class) {
            cache = loadDeserializingClassRenamesCache();
            return cache;
        }
    }
    
    private synchronized static Map<String, String> loadDeserializingClassRenamesCache() {
        if (cache!=null) return cache;
        InputStream resource = new ResourceUtils(DeserializingClassRenamesProvider.class).getResourceFromUrl(DESERIALIZING_CLASS_RENAMES_PROPERTIES_PATH);
        if (resource != null) {
            try {
                Properties props = new Properties();
                props.load(resource);
                
                Map<String, String> result = Maps.newLinkedHashMap();
                for (Enumeration<?> iter = props.propertyNames(); iter.hasMoreElements();) {
                    String key = (String) iter.nextElement();
                    String value = props.getProperty(key);
                    result.put(key, value);
                }
                return result;
            } catch (IOException e) {
                throw Exceptions.propagate(e);
            } finally {
                Streams.closeQuietly(resource);
            }
        } else {
            return ImmutableMap.<String, String>of();
        }
    }

    @Beta
    public static String findMappedName(String name) {
        return Reflections.findMappedNameAndLog(loadDeserializingClassRenames(), name);
    }
}
