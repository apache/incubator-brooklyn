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

import org.slf4j.LoggerFactory;

import brooklyn.basic.BrooklynTypes;

import org.apache.brooklyn.catalog.CatalogItem;
import org.apache.brooklyn.catalog.CatalogItem.CatalogItemType;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.EntityType;
import brooklyn.entity.basic.EntityDynamicType;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.Sensor;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;

import org.apache.brooklyn.policy.Policy;
import org.apache.brooklyn.policy.PolicySpec;
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

import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class CatalogTransformer {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(CatalogTransformer.class);
    
    public static CatalogEntitySummary catalogEntitySummary(BrooklynRestResourceUtils b, CatalogItem<? extends Entity,EntitySpec<?>> item) {
        Set<EntityConfigSummary> config = Sets.newTreeSet(SummaryComparators.nameComparator());
        Set<SensorSummary> sensors = Sets.newTreeSet(SummaryComparators.nameComparator());
        Set<EffectorSummary> effectors = Sets.newTreeSet(SummaryComparators.nameComparator());

        try {
            EntitySpec<?> spec = b.getCatalog().createSpec(item);
            EntityDynamicType typeMap = BrooklynTypes.getDefinedEntityType(spec.getType());
            EntityType type = typeMap.getSnapshot();

            for (ConfigKey<?> x: type.getConfigKeys())
                config.add(EntityTransformer.entityConfigSummary(x, typeMap.getConfigKeyField(x.getName())));
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
            config, sensors, effectors,
            item.isDeprecated(), makeLinks(item));
    }

    @SuppressWarnings("unchecked")
    public static CatalogItemSummary catalogItemSummary(BrooklynRestResourceUtils b, CatalogItem<?,?> item) {
        try {
            switch (item.getCatalogItemType()) {
            case TEMPLATE:
            case ENTITY:
                return catalogEntitySummary(b, (CatalogItem<? extends Entity, EntitySpec<?>>) item);
            case POLICY:
                return catalogPolicySummary(b, (CatalogItem<? extends Policy, PolicySpec<?>>) item);
            case LOCATION:
                return catalogLocationSummary(b, (CatalogItem<? extends Location, LocationSpec<?>>) item);
            default:
                log.warn("Unexpected catalog item type when getting self link (supplying generic item): "+item.getCatalogItemType()+" "+item);
            }
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.warn("Invalid item in catalog when converting REST summaries (supplying generic item), at "+item+": "+e, e);
        }
        return new CatalogItemSummary(item.getSymbolicName(), item.getVersion(), item.getDisplayName(),
            item.getJavaType(), item.getPlanYaml(),
            item.getDescription(), tidyIconLink(b, item, item.getIconUrl()), item.isDeprecated(), makeLinks(item));
    }

    public static CatalogPolicySummary catalogPolicySummary(BrooklynRestResourceUtils b, CatalogItem<? extends Policy,PolicySpec<?>> item) {
        Set<PolicyConfigSummary> config = ImmutableSet.of();
        return new CatalogPolicySummary(item.getSymbolicName(), item.getVersion(), item.getDisplayName(),
                item.getJavaType(), item.getPlanYaml(),
                item.getDescription(), tidyIconLink(b, item, item.getIconUrl()), config,
                item.isDeprecated(), makeLinks(item));
    }

    public static CatalogLocationSummary catalogLocationSummary(BrooklynRestResourceUtils b, CatalogItem<? extends Location,LocationSpec<?>> item) {
        Set<LocationConfigSummary> config = ImmutableSet.of();
        return new CatalogLocationSummary(item.getSymbolicName(), item.getVersion(), item.getDisplayName(),
                item.getJavaType(), item.getPlanYaml(),
                item.getDescription(), tidyIconLink(b, item, item.getIconUrl()), config,
                item.isDeprecated(), makeLinks(item));
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
    
}
