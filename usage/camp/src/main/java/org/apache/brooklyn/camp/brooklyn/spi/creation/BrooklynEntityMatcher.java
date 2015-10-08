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
package org.apache.brooklyn.camp.brooklyn.spi.creation;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampReservedKeys;
import org.apache.brooklyn.camp.spi.PlatformComponentTemplate;
import org.apache.brooklyn.camp.spi.PlatformComponentTemplate.Builder;
import org.apache.brooklyn.camp.spi.pdp.AssemblyTemplateConstructor;
import org.apache.brooklyn.camp.spi.pdp.Service;
import org.apache.brooklyn.camp.spi.resolve.PdpMatcher;
import org.apache.brooklyn.core.catalog.internal.BasicBrooklynCatalog;
import org.apache.brooklyn.core.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.core.mgmt.classloading.JavaBrooklynClassLoadingContext;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class BrooklynEntityMatcher implements PdpMatcher {

    private static final Logger log = LoggerFactory.getLogger(BrooklynEntityMatcher.class);
    
    protected final ManagementContext mgmt;

    public BrooklynEntityMatcher(ManagementContext bmc) {
        this.mgmt = bmc;
    }

    @Override
    public boolean accepts(Object deploymentPlanItem) {
        return lookupType(deploymentPlanItem) != null;
    }

    /** returns the type of the given plan item, 
     * typically whether a Service can be matched to a Brooklyn entity,
     * or null if not supported */
    protected String lookupType(Object deploymentPlanItem) {
        if (deploymentPlanItem instanceof Service) {
            Service service = (Service)deploymentPlanItem;

            String serviceType = service.getServiceType();
            if (serviceType==null) throw new NullPointerException("Service must declare a type ("+service+")");
            BrooklynClassLoadingContext loader = BasicBrooklynCatalog.BrooklynLoaderTracker.getLoader();
            if (loader == null) loader = JavaBrooklynClassLoadingContext.create(mgmt);
            if (BrooklynComponentTemplateResolver.Factory.newInstance(loader, serviceType).canResolve())
                return serviceType;
        }
        return null;
    }

    @Override
    public boolean apply(Object deploymentPlanItem, AssemblyTemplateConstructor atc) {
        if (!(deploymentPlanItem instanceof Service)) return false;
        
        String type = lookupType(deploymentPlanItem);
        if (type==null) return false;

        log.debug("Item "+deploymentPlanItem+" being instantiated with "+type);

        Object old = atc.getInstantiator();
        if (old!=null && !old.equals(BrooklynAssemblyTemplateInstantiator.class)) {
            log.warn("Can't mix Brooklyn entities with non-Brooklyn entities (at present): "+old);
            return false;
        }

        // TODO should we build up a new type, BrooklynEntityComponentTemplate here
        // complete w EntitySpec -- ie merge w BrooklynComponentTemplateResolver ?
        
        Builder<? extends PlatformComponentTemplate> builder = PlatformComponentTemplate.builder();
        builder.type( type.indexOf(':')==-1 ? "brooklyn:"+type : type );
        
        // currently instantiator must be brooklyn at the ATC level
        // optionally would be nice to support multiple/mixed instantiators, 
        // ie at the component level, perhaps with the first one responsible for building the app
        atc.instantiator(BrooklynAssemblyTemplateInstantiator.class);

        String name = ((Service)deploymentPlanItem).getName();
        if (!Strings.isBlank(name)) builder.name(name);
        
        // configuration
        Map<String, Object> attrs = MutableMap.copyOf( ((Service)deploymentPlanItem).getCustomAttributes() );

        if (attrs.containsKey("id"))
            builder.customAttribute("planId", attrs.remove("id"));

        Object location = attrs.remove("location");
        if (location!=null)
            builder.customAttribute("location", location);
        Object locations = attrs.remove("locations");
        if (locations!=null)
            builder.customAttribute("locations", locations);

        MutableMap<Object, Object> brooklynFlags = MutableMap.of();
        Object origBrooklynFlags = attrs.remove(BrooklynCampReservedKeys.BROOKLYN_FLAGS);
        if (origBrooklynFlags!=null) {
            if (!(origBrooklynFlags instanceof Map))
                throw new IllegalArgumentException("brooklyn.flags must be a map of brooklyn flags");
            brooklynFlags.putAll((Map<?,?>)origBrooklynFlags);
        }

        addCustomMapAttributeIfNonNull(builder, attrs, BrooklynCampReservedKeys.BROOKLYN_CONFIG);
        addCustomListAttributeIfNonNull(builder, attrs, BrooklynCampReservedKeys.BROOKLYN_POLICIES);
        addCustomListAttributeIfNonNull(builder, attrs, BrooklynCampReservedKeys.BROOKLYN_ENRICHERS);
        addCustomListAttributeIfNonNull(builder, attrs, BrooklynCampReservedKeys.BROOKLYN_INITIALIZERS);
        addCustomListAttributeIfNonNull(builder, attrs, BrooklynCampReservedKeys.BROOKLYN_CHILDREN);
        addCustomMapAttributeIfNonNull(builder, attrs, BrooklynCampReservedKeys.BROOKLYN_CATALOG);

        brooklynFlags.putAll(attrs);
        if (!brooklynFlags.isEmpty()) {
            builder.customAttribute(BrooklynCampReservedKeys.BROOKLYN_FLAGS, brooklynFlags);
        }

        atc.add(builder.build());

        return true;
    }

    /**
     * Looks for the given key in the map of attributes and adds it to the given builder
     * as a custom attribute with type List.
     * @throws java.lang.IllegalArgumentException if map[key] is not an instance of List
     */
    private void addCustomListAttributeIfNonNull(Builder<? extends PlatformComponentTemplate> builder, Map<?,?> attrs, String key) {
        Object items = attrs.remove(key);
        if (items != null) {
            if (items instanceof List) {
                List<?> itemList = (List<?>) items;
                if (!itemList.isEmpty()) {
                    builder.customAttribute(key, Lists.newArrayList(itemList));
                }
            } else {
                throw new IllegalArgumentException(key + " must be a list, is: " + items.getClass().getName());
            }
        }
    }

    /**
     * Looks for the given key in the map of attributes and adds it to the given builder
     * as a custom attribute with type Map.
     * @throws java.lang.IllegalArgumentException if map[key] is not an instance of Map
     */
    private void addCustomMapAttributeIfNonNull(Builder<? extends PlatformComponentTemplate> builder, Map<?,?> attrs, String key) {
        Object items = attrs.remove(key);
        if (items != null) {
            if (items instanceof Map) {
                Map<?, ?> itemMap = (Map<?, ?>) items;
                if (!itemMap.isEmpty()) {
                    builder.customAttribute(key, Maps.newHashMap(itemMap));
                }
            } else {
                throw new IllegalArgumentException(key + " must be a map, is: " + items.getClass().getName());
            }
        }
    }

}
