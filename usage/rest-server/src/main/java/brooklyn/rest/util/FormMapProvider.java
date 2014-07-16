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
package brooklyn.rest.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sun.jersey.core.impl.provider.entity.FormMultivaluedMapProvider;
import com.sun.jersey.core.util.MultivaluedMapImpl;

/**
 * A MessageBodyReader producing a <code>Map&lt;String, Object&gt;</code>, where Object
 * is either a <code>String</code>, a <code>List&lt;String&gt;</code> or null.
 */
@Provider
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
public class FormMapProvider implements MessageBodyReader<Map<String, Object>> {

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        if (!Map.class.equals(type) || !(genericType instanceof ParameterizedType)) {
            return false;
        }
        ParameterizedType parameterized = (ParameterizedType) genericType;
        return parameterized.getActualTypeArguments().length == 2 &&
                parameterized.getActualTypeArguments()[0] == String.class &&
                parameterized.getActualTypeArguments()[1] == Object.class;
    }

    @Override
    public Map<String, Object> readFrom(Class<Map<String, Object>> type, Type genericType, Annotation[] annotations,
            MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
            throws IOException, WebApplicationException {
        FormMultivaluedMapProvider delegate = new FormMultivaluedMapProvider();
        MultivaluedMap<String, String> multi = new MultivaluedMapImpl();
        multi = delegate.readFrom(multi, mediaType, entityStream);

        Map<String, Object> map = Maps.newHashMapWithExpectedSize(multi.keySet().size());
        for (String key : multi.keySet()) {
            List<String> value = multi.get(key);
            if (value.size() > 1) {
                map.put(key, Lists.newArrayList(value));
            } else if (value.size() == 1) {
                map.put(key, Iterables.getOnlyElement(value));
            } else {
                map.put(key, null);
            }
        }
        return map;
    }

}
