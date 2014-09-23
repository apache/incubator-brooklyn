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

import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import brooklyn.basic.AbstractBrooklynObject;
import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.internal.BasicBrooklynCatalog.BrooklynLoaderTracker;
import brooklyn.entity.rebind.BasicCatalogItemRebindSupport;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.management.ManagementContext;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.management.classloading.BrooklynClassLoadingContextSequential;
import brooklyn.management.classloading.JavaBrooklynClassLoadingContext;
import brooklyn.management.classloading.OsgiBrooklynClassLoadingContext;
import brooklyn.mementos.CatalogItemMemento;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.SetFromFlag;

public abstract class CatalogItemDtoAbstract<T, SpecT> extends AbstractBrooklynObject implements CatalogItem<T, SpecT> {

    // TODO are ID and registeredType the same?
    @SetFromFlag Set<Object> tags = Sets.newLinkedHashSet();
    @SetFromFlag String registeredType;
    @SetFromFlag String javaType;
    @SetFromFlag String description;
    @SetFromFlag String iconUrl;
    @SetFromFlag String version;
    @SetFromFlag CatalogLibrariesDto libraries;
    @SetFromFlag String planYaml;

    // Field left named `name' to maintain the name element in existing catalogues.
    @SetFromFlag("displayName") String name;

    /** @deprecated since 0.7.0.
     * used for backwards compatibility when deserializing.
     * when catalogs are converted to new yaml format, this can be removed. */
    @Deprecated
    @SetFromFlag
    String type;

    // TODO: Items with neither id nor java type should be rejected at construction.
    // XStream auto-filling fields makes this a bit awkward.
    @Override
    public String getId() {
        return super.getId() != null ? super.getId() : getJavaType();
    }

    @Override
    public String getRegisteredTypeName() {
        if (registeredType!=null) return registeredType;
        return getJavaType();
    }

    @Override
    public String getJavaType() {
        if (javaType!=null) return javaType;
        if (type!=null) return type;
        return null;
    }

    @Deprecated
    public String getName() {
        return getDisplayName();
    }

    @Override
    public String getDisplayName() {
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
    public String getVersion() {
        return version;
    }

    @Nonnull
    @Override
    public CatalogItemLibraries getLibraries() {
        return getLibrariesDto();
    }

    public CatalogLibrariesDto getLibrariesDto() {
        return libraries;
    }

    @Nullable @Override
    public String getPlanYaml() {
        return planYaml;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+"["+getId()+"/"+getName()+"]";
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
    public BrooklynClassLoadingContext newClassLoadingContext(final ManagementContext mgmt) {
        BrooklynClassLoadingContextSequential result = new BrooklynClassLoadingContextSequential(mgmt);

        if (getLibraries()!=null && getLibraries().getBundles()!=null && !getLibraries().getBundles().isEmpty())
            // TODO getLibraries() should never be null but sometimes it is still
            // e.g. run CatalogResourceTest without the above check
            result.add(new OsgiBrooklynClassLoadingContext(mgmt, getLibraries().getBundles()));

        BrooklynClassLoadingContext next = BrooklynLoaderTracker.getLoader();
        if (next==null) next = JavaBrooklynClassLoadingContext.newDefault(mgmt);
        result.add(next);

        return result;
    }

    @Override
    public RebindSupport<CatalogItemMemento> getRebindSupport() {
        return new BasicCatalogItemRebindSupport(this);
    }

    @Override
    public void setDisplayName(String newName) {
        this.name = newName;
    }

    @Override
    protected AbstractBrooklynObject configure(Map<?, ?> flags) {
        FlagUtils.setFieldsFromFlags(flags, this);
        return this;
    }

    @Override
    public TagSupport getTagSupport() {
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

}
