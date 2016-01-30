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
import java.util.concurrent.atomic.AtomicInteger;

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

public class CatalogTransformer {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(CatalogTransformer.class);
    
    public static <T extends Entity> CatalogEntitySummary catalogEntitySummary(BrooklynRestResourceUtils b, CatalogItem<T,EntitySpec<? extends T>> item) {
        Set<EntityConfigSummary> config = Sets.newLinkedHashSet();
        Set<SensorSummary> sensors = Sets.newTreeSet(SummaryComparators.nameComparator());
        Set<EffectorSummary> effectors = Sets.newTreeSet(SummaryComparators.nameComparator());

        EntitySpec<?> spec = null;
        try {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            // the raw type isn't needed according to eclipse IDE, but jenkins maven fails without it;
            // must be a java version or compiler thing. don't remove even though it looks okay without it!
            EntitySpec<?> specRaw = (EntitySpec<?>) b.getCatalog().createSpec((CatalogItem) item);
            spec = specRaw;
            EntityDynamicType typeMap = BrooklynTypes.getDefinedEntityType(spec.getType());
            EntityType type = typeMap.getSnapshot();

            AtomicInteger paramPriorityCnt = new AtomicInteger();
            for (SpecParameter<?> input: spec.getParameters()) {
                config.add(EntityTransformer.entityConfigSummary(input, paramPriorityCnt));
                if (input.getSensor()!=null)
                    sensors.add(SensorTransformer.sensorSummaryForCatalog(input.getSensor()));
            }
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
            item.getDescription(), tidyIconLink(b, item, item.getIconUrl()),
            makeTags(spec, item), config, sensors, effectors,
            item.isDeprecated(), makeLinks(item));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static CatalogItemSummary catalogItemSummary(BrooklynRestResourceUtils b, CatalogItem item) {
        try {
            switch (item.getCatalogItemType()) {
            case TEMPLATE:
            case ENTITY:
                return catalogEntitySummary(b, item);
            case POLICY:
                return catalogPolicySummary(b, item);
            case LOCATION:
                return catalogLocationSummary(b, item);
            default:
                log.warn("Unexpected catalog item type when getting self link (supplying generic item): "+item.getCatalogItemType()+" "+item);
            }
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.warn("Invalid item in catalog when converting REST summaries (supplying generic item), at "+item+": "+e, e);
        }
        return new CatalogItemSummary(item.getSymbolicName(), item.getVersion(), item.getDisplayName(),
            item.getJavaType(), item.getPlanYaml(),
            item.getDescription(), tidyIconLink(b, item, item.getIconUrl()), item.tags().getTags(), item.isDeprecated(), makeLinks(item));
    }

    public static CatalogPolicySummary catalogPolicySummary(BrooklynRestResourceUtils b, CatalogItem<? extends Policy,PolicySpec<?>> item) {
        Set<PolicyConfigSummary> config = ImmutableSet.of();
        return new CatalogPolicySummary(item.getSymbolicName(), item.getVersion(), item.getDisplayName(),
                item.getJavaType(), item.getPlanYaml(),
                item.getDescription(), tidyIconLink(b, item, item.getIconUrl()), config,
                item.tags().getTags(), item.isDeprecated(), makeLinks(item));
    }

    public static CatalogLocationSummary catalogLocationSummary(BrooklynRestResourceUtils b, CatalogItem<? extends Location,LocationSpec<?>> item) {
        Set<LocationConfigSummary> config = ImmutableSet.of();
        return new CatalogLocationSummary(item.getSymbolicName(), item.getVersion(), item.getDisplayName(),
                item.getJavaType(), item.getPlanYaml(),
                item.getDescription(), tidyIconLink(b, item, item.getIconUrl()), config,
                item.tags().getTags(), item.isDeprecated(), makeLinks(item));
    }

    protected static Map<String, URI> makeLinks(CatalogItem<?,?> item) {
        return MutableMap.<String, URI>of().addIfNotNull("self", URI.create(getSelfLink(item)));
    }

    protected static String getSelfLink(CatalogItem<?,?> item) {
        String itemId = item.getId();
        switch (item.getCatalogItemType()) {
        case TEMPLATE:
            return "/v1/applications/" + itemId + "/" + item.getVersion();
        case ENTITY:
            return "/v1/entities/" + itemId + "/" + item.getVersion();
        case POLICY:
            return "/v1/policies/" + itemId + "/" + item.getVersion();
        case LOCATION:
            return "/v1/locations/" + itemId + "/" + item.getVersion();
        default:
            log.warn("Unexpected catalog item type when getting self link (not supplying self link): "+item.getCatalogItemType()+" "+item);
            return null;
        }
    }
    private static String tidyIconLink(BrooklynRestResourceUtils b, CatalogItem<?,?> item, String iconUrl) {
        if (b.isUrlServerSideAndSafe(iconUrl))
            return "/v1/catalog/icon/"+item.getSymbolicName() + "/" + item.getVersion();
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
