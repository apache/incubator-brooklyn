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
package brooklyn.catalog.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogItem.CatalogBundle;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class CatalogLibrariesDto implements CatalogItem.CatalogItemLibraries {

    private static Logger LOG = LoggerFactory.getLogger(CatalogLibrariesDto.class);

    private Collection<CatalogBundle> bundles = new CopyOnWriteArrayList<CatalogBundle>();

    public void addBundle(String name, String version, String url) {
        Preconditions.checkNotNull(bundles, "Cannot add a bundle to a deserialized DTO");
        if (name == null && version == null) {
            Preconditions.checkNotNull(url, "url");
        } else {
            Preconditions.checkNotNull(name, "name");
            Preconditions.checkNotNull(version, "version");
        }
        
        bundles.add(new CatalogBundleDto(name, version, url));
    }

    /**
     * @return An immutable copy of the bundle URLs referenced by this object
     */
    @Override
    public Collection<CatalogBundle> getBundles() {
        if (bundles == null) {
            // can be null on deserialization
            return Collections.emptyList();
        }
        return ImmutableList.copyOf(bundles);
    }

    /**
     * Parses an instance of CatalogLibrariesDto from the given List. Expects the list entries
     * to be either Strings or Maps of String -> String. Will skip items that are not.
     */
    public static CatalogLibrariesDto from(Collection<?> possibleLibraries) {
        CatalogLibrariesDto dto = new CatalogLibrariesDto();
        for (Object object : possibleLibraries) {
            if (object instanceof Map) {
                Map<?, ?> entry = (Map<?, ?>) object;
                String name = stringValOrNull(entry, "name");
                String version = stringValOrNull(entry, "version");
                String url = stringValOrNull(entry, "url");
                dto.addBundle(name, version, url);
            } else if (object instanceof String) {
                String inlineRef = (String) object;

                final String name;
                final String version;
                final String url;

                //Infer reference type (heuristically)
                if (inlineRef.contains("/") || inlineRef.contains("\\")) {
                    //looks like an url/file path
                    name = null;
                    version = null;
                    url = inlineRef;
                } else if (inlineRef.indexOf(CatalogUtils.VERSION_DELIMITER) != -1) {
                    //looks like a name+version ref
                    name = CatalogUtils.getIdFromVersionedId(inlineRef);
                    version = CatalogUtils.getVersionFromVersionedId(inlineRef);
                    url = null;
                } else {
                    //assume it to be relative url
                    name = null;
                    version = null;
                    url = inlineRef;
                }

                dto.addBundle(name, version, url);
            } else {
                LOG.debug("Unexpected entry in libraries list neither string nor map: " + object);
            }
        }
        return dto;
    }

    private static String stringValOrNull(Map<?, ?> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }
}
