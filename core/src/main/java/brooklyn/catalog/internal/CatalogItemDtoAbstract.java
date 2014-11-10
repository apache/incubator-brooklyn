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
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.AbstractBrooklynObject;
import brooklyn.catalog.CatalogItem;
import brooklyn.entity.rebind.BasicCatalogItemRebindSupport;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.mementos.CatalogItemMemento;
import brooklyn.util.collections.MutableList;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public abstract class CatalogItemDtoAbstract<T, SpecT> extends AbstractBrooklynObject implements CatalogItem<T, SpecT> {

    private static Logger LOG = LoggerFactory.getLogger(CatalogItemDtoAbstract.class);

    private @SetFromFlag String symbolicName;
    private @SetFromFlag String version = BasicBrooklynCatalog.NO_VERSION;

    /**@deprecated since 0.7.0, left for deserialization backwards compatibility */
    private @Deprecated @SetFromFlag String registeredTypeName;

    private @SetFromFlag String displayName;
    /**@deprecated since 0.7.0, left for deserialization backwards compatibility */
    private @Deprecated @SetFromFlag String name;
    private @SetFromFlag String description;
    private @SetFromFlag String iconUrl;

    private @SetFromFlag String javaType;
    /**@deprecated since 0.7.0, left for deserialization backwards compatibility */
    private @Deprecated @SetFromFlag String type;
    private @SetFromFlag String planYaml;

    private @SetFromFlag Collection<CatalogBundle> bundles;
    /**@deprecated since 0.7.0, left for deserialization backwards compatibility */
    private @Deprecated @SetFromFlag CatalogLibrariesDto libraries;
    private @SetFromFlag Set<Object> tags = Sets.newLinkedHashSet();

    @Override
    public String getId() {
        return getCatalogItemId();
    }

    @Override
    public String getCatalogItemId() {
        return CatalogUtils.getVersionedId(getSymbolicName(), getVersion());
    }

    @Override
    public String getJavaType() {
        if (javaType != null) return javaType;
        return type;
    }

    @Deprecated
    public String getName() {
        return getDisplayName();
    }

    @Override
    public String getDisplayName() {
        if (displayName != null) return displayName;
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getIconUrl() {
        return iconUrl;
    }

    @Override
    public String getSymbolicName() {
        if (symbolicName != null) return symbolicName;
        if (registeredTypeName != null) return registeredTypeName;
        return getJavaType();
    }

    @Override
    public String getVersion() {
        //xstream doesn't call constructors
        //the object is used directly (instead of a memento) when
        //deserializing directly from catalog.xml
        if (version != null) {
            return version;
        } else {
            return BasicBrooklynCatalog.NO_VERSION;
        }
    }

    @Nonnull
    @Override
    @SuppressWarnings("deprecation")
    public Collection<CatalogBundle> getLibraries() {
        if (bundles != null) {
            return ImmutableList.copyOf(bundles);
        } else if (libraries != null && libraries.getBundles() != null) {
            return ImmutableList.copyOf(libraries.getBundles());
        } else {
            return Collections.emptyList();
        }
    }

    @Nullable @Override
    public String getPlanYaml() {
        return planYaml;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+"["+getId()+"/"+getDisplayName()+"]";
    }

    public abstract Class<SpecT> getSpecType();

    transient CatalogXmlSerializer serializer;

    @Override
    public String toXmlString() {
        if (serializer==null) loadSerializer();
        return serializer.toString(this);
    }

    private synchronized void loadSerializer() {
        if (serializer == null) {
            serializer = new CatalogXmlSerializer();
        }
    }

    @Override
    public RebindSupport<CatalogItemMemento> getRebindSupport() {
        return new BasicCatalogItemRebindSupport(this);
    }

    @Override
    public void setDisplayName(String newName) {
        this.displayName = newName;
        this.name = null;
    }

    @Override
    protected AbstractBrooklynObject configure(Map<?, ?> flags) {
        FlagUtils.setFieldsFromFlags(flags, this);
        return this;
    }

    @Override
    public TagSupport tags() {
        return new BasicTagSupport();
    }

    /*
     * Using a custom tag support class rather than the one in AbstractBrooklynObject because
     * when XStream unmarshals a catalog item with no tags (e.g. from any catalog.xml file)
     * super.tags will be null, and any call to getTags throws a NullPointerException on the
     * synchronized (tags) statement. It can't just be initialised here because super.tags is
     * final.
     */
    private class BasicTagSupport implements TagSupport {

        private void setTagsIfNull() {
            // Possible if the class was unmarshalled by Xstream with no tags
            synchronized (CatalogItemDtoAbstract.this) {
                if (tags == null) {
                    tags = Sets.newLinkedHashSet();
                }
            }
        }

        @Nonnull
        @Override
        public Set<Object> getTags() {
            synchronized (CatalogItemDtoAbstract.this) {
                setTagsIfNull();
                return ImmutableSet.copyOf(tags);
            }
        }

        @Override
        public boolean containsTag(Object tag) {
            synchronized (CatalogItemDtoAbstract.this) {
                setTagsIfNull();
                return tags.contains(tag);
            }
        }

        @Override
        public boolean addTag(Object tag) {
            boolean result;
            synchronized (CatalogItemDtoAbstract.this) {
                setTagsIfNull();
                result = tags.add(tag);
            }
            onTagsChanged();
            return result;
        }

        @Override
        public boolean addTags(Iterable<?> newTags) {
            boolean result;
            synchronized (CatalogItemDtoAbstract.this) {
                setTagsIfNull();
                result = Iterables.addAll(tags, newTags);
            }
            onTagsChanged();
            return result;
        }

        @Override
        public boolean removeTag(Object tag) {
            boolean result;
            synchronized (CatalogItemDtoAbstract.this) {
                setTagsIfNull();
                result = tags.remove(tag);
            }
            onTagsChanged();
            return result;
        }
    }

    @Override
    @Deprecated
    public void setCatalogItemId(String id) {
        //no op, should be used by rebind code only
    }

    protected void setSymbolicName(String symbolicName) {
        this.symbolicName = symbolicName;
        this.registeredTypeName = null;
    }

    protected void setVersion(String version) {
        this.version = version;
    }

    protected void setDescription(String description) {
        this.description = description;
    }

    protected void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    protected void setJavaType(String javaType) {
        this.javaType = javaType;
        this.type = null;
    }

    protected void setPlanYaml(String planYaml) {
        this.planYaml = planYaml;
    }

    protected void setLibraries(Collection<CatalogBundle> libraries) {
        this.bundles = libraries;
    }

    protected void setTags(Set<Object> tags) {
        this.tags = tags;
    }

    protected void setSerializer(CatalogXmlSerializer serializer) {
        this.serializer = serializer;
    }

    /**
     * Parses an instance of CatalogLibrariesDto from the given List. Expects the list entries
     * to be either Strings or Maps of String -> String. Will skip items that are not.
     */
    public static Collection<CatalogBundle> parseLibraries(Collection<?> possibleLibraries) {
        Collection<CatalogBundle> dto = MutableList.of();
        for (Object object : possibleLibraries) {
            if (object instanceof Map) {
                Map<?, ?> entry = (Map<?, ?>) object;
                String name = stringValOrNull(entry, "name");
                String version = stringValOrNull(entry, "version");
                String url = stringValOrNull(entry, "url");
                dto.add(new CatalogBundleDto(name, version, url));
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

                dto.add(new CatalogBundleDto(name, version, url));
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
