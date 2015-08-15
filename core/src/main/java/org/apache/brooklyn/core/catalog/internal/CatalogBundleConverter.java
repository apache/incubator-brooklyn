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
package org.apache.brooklyn.core.catalog.internal;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;


/**
 *  Convert old-style catalog.xml file formats to the latest version.
 *  The code is needed only during transition to the new version, can be removed after a while.
 */
@Deprecated
public class CatalogBundleConverter implements Converter {

    private ReflectionConverter delegateConverter;

    public CatalogBundleConverter(Mapper mapper, ReflectionProvider reflectionProvider) {
        this.delegateConverter = new ReflectionConverter(mapper, reflectionProvider);
    }

    @Override
    public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
        return type == CatalogBundleDto.class;
    }

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        context.convertAnother(source, delegateConverter);
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        if (reader.hasMoreChildren()) {
            return context.convertAnother(context.currentObject(), CatalogBundleDto.class, delegateConverter);
        } else {
            return new CatalogBundleDto(null, null, reader.getValue());
        }
    }

}
