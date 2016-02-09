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
package org.apache.brooklyn.rest.transform;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogItemType;
import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.EntityType;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.objs.SpecParameter;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.core.entity.EntityDynamicType;
import org.apache.brooklyn.core.mgmt.BrooklynTags;
import org.apache.brooklyn.core.objs.BrooklynTypes;
import org.apache.brooklyn.rest.domain.CatalogEntitySummary;
import org.apache.brooklyn.rest.domain.CatalogItemSummary;
import org.apache.brooklyn.rest.domain.CatalogLocationSummary;
import org.apache.brooklyn.rest.domain.CatalogPolicySummary;
import org.apache.brooklyn.rest.domain.EffectorSummary;
import org.apache.brooklyn.rest.domain.EntityConfigSummary;
import org.apache.brooklyn.rest.domain.LocationConfigSummary;
import org.apache.brooklyn.rest.domain.PolicyConfigSummary;
import org.apache.brooklyn.rest.domain.SensorSummary;
import org.apache.brooklyn.rest.domain.SummaryComparators;
import org.apache.brooklyn.rest.util.BrooklynRestResourceUtils;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.javalang.Reflections;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import javax.ws.rs.core.UriBuilder;
import org.apache.brooklyn.rest.api.CatalogApi;
import static org.apache.brooklyn.rest.util.WebResourceUtils.serviceUriBuilder;

public class CatalogTransformer {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(CatalogTransformer.class);
    
    public static <T extends Entity> CatalogEntitySummary catalogEntitySummary(BrooklynRestResourceUtils b, CatalogItem<T,EntitySpec<? extends T>> item, UriBuilder ub) {
        Set<EntityConfigSummary> config = Sets.newLinkedHashSet();
        Set<SensorSummary> sensors = Sets.newTreeSet(SummaryComparators.nameComparator());
        Set<EffectorSummary> effectors = Sets.newTreeSet(SummaryComparators.nameComparator());

        EntitySpec<?> spec = null;

        try {
            spec = (EntitySpec<?>) b.getCatalog().createSpec((CatalogItem) item);
            EntityDynamicType typeMap = BrooklynTypes.getDefinedEntityType(spec.getType());
            EntityType type = typeMap.getSnapshot();

            for (SpecParameter<?> input: spec.getParameters())
                config.add(EntityTransformer.entityConfigSummary(input));
            for (Sensor<?> x: type.getSensors())
                sensors.add(SensorTransformer.sensorSummaryForCatalog(x));
            for (Effector<?> x: type.getEffectors())
                effectors.add(EffectorTransformer.effectorSummaryForCatalog(x));

        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            
            // templates with multiple entities can't have spec created in the manner above; just ignore
            if (item.getCatalogItemType()==CatalogItemType.ENTITY) {
                log.warn("Unable to create spec for "+item+": "+e, e);
            }
            if (log.isTraceEnabled()) {
                log.trace("Unable to create spec for "+item+": "+e, e);
            }
        }
        
        return new CatalogEntitySummary(item.getSymbolicName(), item.getVersion(), item.getDisplayName(),
            item.getJavaType(), item.getPlanYaml(),
            item.getDescription(), tidyIconLink(b, item, item.getIconUrl(), ub),
            makeTags(spec, item), config, sensors, effectors,
            item.isDeprecated(), makeLinks(item, ub));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static CatalogItemSummary catalogItemSummary(BrooklynRestResourceUtils b, CatalogItem item, UriBuilder ub) {
        try {
            switch (item.getCatalogItemType()) {
            case TEMPLATE:
            case ENTITY:
                return catalogEntitySummary(b, item, ub);
            case POLICY:
                return catalogPolicySummary(b, item, ub);
            case LOCATION:
                return catalogLocationSummary(b, item, ub);
            default:
                log.warn("Unexpected catalog item type when getting self link (supplying generic item): "+item.getCatalogItemType()+" "+item);
            }
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.warn("Invalid item in catalog when converting REST summaries (supplying generic item), at "+item+": "+e, e);
        }
        return new CatalogItemSummary(item.getSymbolicName(), item.getVersion(), item.getDisplayName(),
            item.getJavaType(), item.getPlanYaml(),
            item.getDescription(), tidyIconLink(b, item, item.getIconUrl(), ub), item.tags().getTags(), item.isDeprecated(), makeLinks(item, ub));
    }

    public static CatalogPolicySummary catalogPolicySummary(BrooklynRestResourceUtils b, CatalogItem<? extends Policy,PolicySpec<?>> item, UriBuilder ub) {
        Set<PolicyConfigSummary> config = ImmutableSet.of();
        return new CatalogPolicySummary(item.getSymbolicName(), item.getVersion(), item.getDisplayName(),
                item.getJavaType(), item.getPlanYaml(),
                item.getDescription(), tidyIconLink(b, item, item.getIconUrl(), ub), config,
                item.tags().getTags(), item.isDeprecated(), makeLinks(item, ub));
    }

    public static CatalogLocationSummary catalogLocationSummary(BrooklynRestResourceUtils b, CatalogItem<? extends Location,LocationSpec<?>> item, UriBuilder ub) {
        Set<LocationConfigSummary> config = ImmutableSet.of();
        return new CatalogLocationSummary(item.getSymbolicName(), item.getVersion(), item.getDisplayName(),
                item.getJavaType(), item.getPlanYaml(),
                item.getDescription(), tidyIconLink(b, item, item.getIconUrl(), ub), config,
                item.tags().getTags(), item.isDeprecated(), makeLinks(item, ub));
    }

    protected static Map<String, URI> makeLinks(CatalogItem<?,?> item, UriBuilder ub) {
        return MutableMap.<String, URI>of().addIfNotNull("self", getSelfLink(item, ub));
    }

    protected static URI getSelfLink(CatalogItem<?,?> item, UriBuilder ub) {
        String itemId = item.getId();
        switch (item.getCatalogItemType()) {
        case TEMPLATE:
            return serviceUriBuilder(ub, CatalogApi.class, "getApplication").build(itemId, item.getVersion());
        case ENTITY:
            return serviceUriBuilder(ub, CatalogApi.class, "getEntity").build(itemId, item.getVersion());
        case POLICY:
            return serviceUriBuilder(ub, CatalogApi.class, "getPolicy").build(itemId, item.getVersion());
        case LOCATION:
            return serviceUriBuilder(ub, CatalogApi.class, "getLocation").build(itemId, item.getVersion());
        default:
            log.warn("Unexpected catalog item type when getting self link (not supplying self link): "+item.getCatalogItemType()+" "+item);
            return null;
        }
    }
    private static String tidyIconLink(BrooklynRestResourceUtils b, CatalogItem<?,?> item, String iconUrl, UriBuilder ub) {
        if (b.isUrlServerSideAndSafe(iconUrl)) {
            return serviceUriBuilder(ub, CatalogApi.class, "getIcon").build(item.getSymbolicName(), item.getVersion()).toString();
        }
        return iconUrl;
    }

    private static Set<Object> makeTags(EntitySpec<?> spec, CatalogItem<?, ?> item) {
        // Combine tags on item with an InterfacesTag.
        Set<Object> tags = MutableSet.copyOf(item.tags().getTags());
        if (spec != null) {
            Class<?> type;
            if (spec.getImplementation() != null) {
                type = spec.getImplementation();
            } else {
                type = spec.getType();
            }
            if (type != null) {
                tags.add(new BrooklynTags.TraitsTag(Reflections.getAllInterfaces(type)));
            }
        }
        return tags;
    }
    
}
