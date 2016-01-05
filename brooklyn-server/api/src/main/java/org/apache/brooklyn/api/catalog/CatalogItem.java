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
package org.apache.brooklyn.api.catalog;

import java.util.Collection;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.rebind.RebindSupport;
import org.apache.brooklyn.api.mgmt.rebind.Rebindable;
import org.apache.brooklyn.api.mgmt.rebind.mementos.CatalogItemMemento;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.typereg.OsgiBundleWithUrl;

import com.google.common.annotations.Beta;

@Beta
public interface CatalogItem<T,SpecT> extends BrooklynObject, Rebindable {
    
    public static enum CatalogItemType {
        TEMPLATE, 
        ENTITY, 
        POLICY,
        LOCATION;
        
        public static CatalogItemType ofSpecClass(Class<? extends AbstractBrooklynObjectSpec<?, ?>> type) {
            if (type==null) return null;
            if (PolicySpec.class.isAssignableFrom(type)) return POLICY;
            if (LocationSpec.class.isAssignableFrom(type)) return LOCATION;
            if (EntitySpec.class.isAssignableFrom(type)) return ENTITY;
            return null;
        }
        public static CatalogItemType ofTargetClass(Class<? extends BrooklynObject> type) {
            if (type==null) return null;
            if (Policy.class.isAssignableFrom(type)) return POLICY;
            if (Location.class.isAssignableFrom(type)) return LOCATION;
            if (Application.class.isAssignableFrom(type)) return TEMPLATE;
            if (Entity.class.isAssignableFrom(type)) return ENTITY;
            return null;
        }
    }
    
    public static interface CatalogBundle extends OsgiBundleWithUrl {
        /** @deprecated since 0.9.0, use {@link #isNameResolved()} */
        public boolean isNamed();
    }

    /**
     * @throws UnsupportedOperationException; config not supported for catalog items
     */
    @Override
    ConfigurationSupport config();

    /**
     * @throws UnsupportedOperationException; subscriptions are not supported for catalog items
     */
    @Override
    SubscriptionSupport subscriptions();

    /** @deprecated since 0.7.0 in favour of {@link CatalogBundle}, kept for rebind compatibility */
    @Deprecated
    public static interface CatalogItemLibraries {
        Collection<String> getBundles();
    }

    public CatalogItemType getCatalogItemType();

    /** @return The high-level type of this entity, e.g. Entity (not a specific Entity class) */
    public Class<T> getCatalogItemJavaType();

    /** @return The type of the spec e.g. EntitySpec corresponding to {@link #getCatalogItemJavaType()} */
    public Class<SpecT> getSpecType();
    
    /**
     * @return The underlying java type of the item represented, if not described via a YAML spec.
     * Normally null (and the type comes from yaml).
     * @deprecated since 0.9.0. Use plan based items instead ({@link #getPlanYaml()})
     */
    @Deprecated
    @Nullable public String getJavaType();

    /** @deprecated since 0.7.0. Use {@link #getDisplayName} */
    @Deprecated
    public String getName();

    /** @deprecated since 0.7.0. Use {@link #getSymbolicName} */
    @Deprecated
    public String getRegisteredTypeName();

    @Nullable public String getDescription();

    @Nullable public String getIconUrl();

    public String getSymbolicName();

    public String getVersion();

    public Collection<CatalogBundle> getLibraries();

    public String toXmlString();

    /** @return The underlying YAML for this item, if known; 
     * currently including `services:` or `brooklyn.policies:` prefix (but this will likely be removed) */
    @Nullable public String getPlanYaml();

    @Override
    RebindSupport<CatalogItemMemento> getRebindSupport();
    
    /** Built up from {@link #getSymbolicName()} and {@link #getVersion()}.
     * 
     * (It is a bit self-referential having this method on this type of {@link BrooklynObject},
     * but it is easier this than making the interface hierarchy more complicated.) */
    @Override
    public String getCatalogItemId();

    public void setDeprecated(boolean deprecated);

    public void setDisabled(boolean disabled);

    /**
     * @return True if the item has been deprecated (i.e. its use is discouraged)
     */
    boolean isDeprecated();
    
    /**
     * @return True if the item has been disabled (i.e. its use is forbidden, except for pre-existing apps)
     */
    boolean isDisabled();
}
