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
package brooklyn.management.classloading;

import java.net.URL;
import java.util.Collection;
import java.util.Collections;

import org.apache.brooklyn.catalog.CatalogItem;
import org.apache.brooklyn.catalog.CatalogItem.CatalogBundle;
import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.entitlement.EntitlementClass;

import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.management.entitlement.Entitlements;
import brooklyn.management.ha.OsgiManager;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.guava.Maybe;

import com.google.common.base.Objects;

public class OsgiBrooklynClassLoadingContext extends AbstractBrooklynClassLoadingContext {

    private final String catalogItemId;
    private boolean hasBundles = false;
    private transient Collection<CatalogBundle> _bundles;

    public OsgiBrooklynClassLoadingContext(ManagementContext mgmt, String catalogItemId, Collection<CatalogBundle> bundles) {
        super(mgmt);
        this._bundles = bundles;
        this.hasBundles = bundles!=null && !bundles.isEmpty();
        this.catalogItemId = catalogItemId;
    }

    public Collection<CatalogBundle> getBundles() {
        if (_bundles!=null || !hasBundles) return _bundles;
        CatalogItem<?, ?> cat = CatalogUtils.getCatalogItemOptionalVersion(mgmt, catalogItemId);
        if (cat==null) {
            throw new IllegalStateException("Catalog item not found for "+catalogItemId+"; cannot create loading context");
        }
        _bundles = cat.getLibraries();
        return _bundles;
    }
    
    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Maybe<Class<?>> tryLoadClass(String className) {
        Maybe<Class<Object>> clazz = null;
        Maybe<OsgiManager> osgi = null;
        if (mgmt!=null) {
            osgi = ((ManagementContextInternal)mgmt).getOsgiManager();
            if (osgi.isPresent() && getBundles()!=null && !getBundles().isEmpty()) {
                if (!Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_CATALOG_ITEM, catalogItemId))
                    return Maybe.absent("Not entitled to use this catalog entry");
                
                clazz = osgi.get().tryResolveClass(className, getBundles());
                if (clazz.isPresent())
                    return (Maybe)clazz;
            }
        }
        
        if (clazz!=null) { 
            // if OSGi bundles were defined and failed, then use its error message
            return (Maybe)clazz;
        }
        // else determine best message
        if (mgmt==null) return Maybe.absent("No mgmt context available for loading "+className);
        if (osgi!=null && osgi.isAbsent()) return Maybe.absent("OSGi not available on mgmt for loading "+className);
        if (!hasBundles)
            return Maybe.absent("No bundles available for loading "+className);
        return Maybe.absent("Inconsistent state ("+mgmt+"/"+osgi+"/"+getBundles()+" loading "+className);
    }

    @Override
    public String toString() {
        return "OSGi:"+catalogItemId+"["+getBundles()+"]";
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), getBundles(), catalogItemId);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) return false;
        if (!(obj instanceof OsgiBrooklynClassLoadingContext)) return false;

        OsgiBrooklynClassLoadingContext other = (OsgiBrooklynClassLoadingContext)obj;
        if (!catalogItemId.equals(other.catalogItemId)) return false;
        if (!Objects.equal(getBundles(), other.getBundles())) return false;
        return true;
    }

    @Override
    public URL getResource(String name) {
        if (mgmt != null && isEntitledToSeeCatalogItem()) {
            Maybe<OsgiManager> osgi = ((ManagementContextInternal) mgmt).getOsgiManager();
            if (osgi.isPresent() && hasBundles) {
                return osgi.get().getResource(name, getBundles());
            }
        }
        return null;
    }

    @Override
    public Iterable<URL> getResources(String name) {
        if (mgmt != null && isEntitledToSeeCatalogItem()) {
            Maybe<OsgiManager> osgi = ((ManagementContextInternal) mgmt).getOsgiManager();
            if (osgi.isPresent() && hasBundles) {
                return osgi.get().getResources(name, getBundles());
            }
        }
        return Collections.emptyList();
    }

    public String getCatalogItemId() {
        return catalogItemId;
    }

    /**
     * @return true if the current entitlement context may {@link Entitlements#SEE_CATALOG_ITEM see}
     * {@link #getCatalogItemId}.
     */
    private boolean isEntitledToSeeCatalogItem() {
        return Entitlements.isEntitled(mgmt.getEntitlementManager(),
                Entitlements.SEE_CATALOG_ITEM,
                catalogItemId);
    }

}
