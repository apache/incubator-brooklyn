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
package brooklyn.entity.rebind.persister;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.brooklyn.api.catalog.CatalogItem.CatalogBundle;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogItemLibraries;
import org.apache.brooklyn.core.catalog.internal.CatalogBundleDto;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/**
 *  Convert old-style rebind file formats to the latest version.
 *  The code is needed only during transition to the new version, can be removed after a while.
 */
@Deprecated
public class CatalogItemLibrariesConverter implements Converter {

    @Override
    public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
        return CatalogItemLibraries.class.isAssignableFrom(type) ||
                Collection.class.isAssignableFrom(type);
    }

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        context.convertAnother(source);
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        Object obj = context.convertAnother(context.currentObject(), context.getRequiredType());
        if (CatalogItemLibraries.class.isAssignableFrom(context.getRequiredType())) {
            CatalogItemLibraries libs = (CatalogItemLibraries)obj;
            Collection<String> bundles = libs.getBundles();
            Collection<CatalogBundle> libraries = new ArrayList<CatalogBundle>(bundles.size());
            for (String url : bundles) {
                libraries.add(new CatalogBundleDto(null, null, url));
            }
            return libraries;
        } else {
            return obj;
        }
    }

}
