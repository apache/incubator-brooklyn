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

import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.rebind.RebindSupport;
import org.apache.brooklyn.api.mgmt.rebind.mementos.CatalogItemMemento;
import org.apache.brooklyn.api.objs.SpecParameter;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.objs.BrooklynObjectInternal;
import org.apache.brooklyn.core.relations.EmptyRelationSupport;

import com.google.common.base.Preconditions;

public class CatalogItemDo<T,SpecT> implements CatalogItem<T,SpecT>, BrooklynObjectInternal {

    protected final CatalogDo catalog;
    protected final CatalogItemDtoAbstract<T,SpecT> itemDto;

    protected volatile Class<T> javaClass; 
    
    public CatalogItemDo(CatalogDo catalog, CatalogItem<T,SpecT> itemDto) {
        this.catalog = Preconditions.checkNotNull(catalog, "catalog");
        this.itemDto = (CatalogItemDtoAbstract<T, SpecT>) Preconditions.checkNotNull(itemDto, "itemDto");
    }

    public CatalogItem<T,SpecT> getDto() {
        return itemDto;
    }
    
    /**
     * @throws UnsupportedOperationException; Config not supported for catalog item. See {@link #getPlanYaml()}.
     */
    @Override
    public ConfigurationSupportInternal config() {
        throw new UnsupportedOperationException();
    }

    /**
     * @throws UnsupportedOperationException; subscriptions are not supported for catalog items
     */
    @Override
    public SubscriptionSupportInternal subscriptions() {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Overrides the parent so that relations are not visible.
     * @return an immutable empty relation support object; relations are not supported,
     * but we do not throw on access to enable reads in a consistent manner
     */
    @Override
    public RelationSupportInternal<CatalogItem<T,SpecT>> relations() {
        return new EmptyRelationSupport<CatalogItem<T,SpecT>>(this);
    }
    
    @Override
    public <U> U getConfig(ConfigKey<U> key) {
        return config().get(key);
    }
    
    @Override
    public <U> U setConfig(ConfigKey<U> key, U val) {
        return config().set(key, val);
    }

    @Override
    public CatalogItemType getCatalogItemType() {
        return itemDto.getCatalogItemType();
    }

    @Override
    public Class<T> getCatalogItemJavaType() {
        return itemDto.getCatalogItemJavaType();
    }

    @Override
    public String getId() {
        return itemDto.getId();
    }

    @Override
    public String getCatalogItemId() {
        return itemDto.getCatalogItemId();
    }

    @Override
    public void setDeprecated(boolean deprecated) {
        itemDto.setDeprecated(deprecated);
    }

    @Override
    public boolean isDeprecated() {
        return itemDto.isDeprecated();
    }

    @Override
    public void setDisabled(boolean diabled) {
        itemDto.setDisabled(diabled);
    }

    @Override
    public boolean isDisabled() {
        return itemDto.isDisabled();
    }

    @Override
    public void setCatalogItemId(String id) {
        itemDto.setCatalogItemId(id);
    }

    @Override
    public String getJavaType() {
        return itemDto.getJavaType();
    }

    @Deprecated
    @Override
    public String getName() {
        return getDisplayName();
    }

    @Deprecated
    @Override
    public String getRegisteredTypeName() {
        return getSymbolicName();
    }

    @Override
    public String getDisplayName() {
        return itemDto.getDisplayName();
    }

    @Override
    public TagSupport tags() {
        return itemDto.tags();
    }

    @Override
    public String getDescription() {
        return itemDto.getDescription();
    }

    @Override
    public String getIconUrl() {
        return itemDto.getIconUrl();
    }
    
    @Override
    public String getSymbolicName() {
        return itemDto.getSymbolicName();
    }

    @Override
    public String getVersion() {
        return itemDto.getVersion();
    }

    @Nonnull  // but it is still null sometimes, see in CatalogDo.loadJavaClass
    @Override
    public Collection<CatalogBundle> getLibraries() {
        return itemDto.getLibraries();
    }

    /** @deprecated since 0.7.0 this is the legacy mechanism; still needed for policies and apps, but being phased out.
     * new items should use {@link #getPlanYaml} and {@link #newClassLoadingContext} */
    @Deprecated
    public Class<T> getJavaClass() {
        if (javaClass==null) loadJavaClass(null);
        return javaClass;
    }
    
    @SuppressWarnings("unchecked")
    @Deprecated
    Class<? extends T> loadJavaClass(final ManagementContext mgmt) {
        if (javaClass!=null) return javaClass;
        javaClass = (Class<T>)CatalogUtils.newClassLoadingContext(mgmt, getId(), getLibraries(), catalog.getRootClassLoader()).loadClass(getJavaType());
        return javaClass;
    }

    @Override
    public String toString() {
        return getClass().getCanonicalName()+"["+itemDto+"]";
    }

    @Override
    public String toXmlString() {
        return itemDto.toXmlString();
    }

    @Override
    public Class<SpecT> getSpecType() {
        return itemDto.getSpecType();
    }

    @Nullable @Override
    public String getPlanYaml() {
        return itemDto.getPlanYaml();
    }

    @Override
    public RebindSupport<CatalogItemMemento> getRebindSupport() {
        return itemDto.getRebindSupport();
    }
}
