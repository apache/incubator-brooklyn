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
package brooklyn.rest.transform;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.slf4j.LoggerFactory;

import brooklyn.basic.BrooklynTypes;
import brooklyn.catalog.CatalogItem;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.EntityType;
import brooklyn.entity.basic.EntityDynamicType;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.Sensor;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;
import brooklyn.rest.domain.CatalogEntitySummary;
import brooklyn.rest.domain.CatalogItemSummary;
import brooklyn.rest.domain.CatalogPolicySummary;
import brooklyn.rest.domain.EffectorSummary;
import brooklyn.rest.domain.EntityConfigSummary;
import brooklyn.rest.domain.PolicyConfigSummary;
import brooklyn.rest.domain.SensorSummary;
import brooklyn.rest.domain.SummaryComparators;
import brooklyn.rest.util.BrooklynRestResourceUtils;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class CatalogTransformer {

    @SuppressWarnings("unused")
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(CatalogTransformer.class);
    
    public static CatalogEntitySummary catalogEntitySummary(BrooklynRestResourceUtils b, CatalogItem<? extends Entity,EntitySpec<?>> item) {
        EntitySpec<?> spec = b.getCatalog().createSpec(item);
        EntityDynamicType typeMap = BrooklynTypes.getDefinedEntityType(spec.getType());
        EntityType type = typeMap.getSnapshot();

        Set<EntityConfigSummary> config = Sets.newTreeSet(SummaryComparators.nameComparator());
        Set<SensorSummary> sensors = Sets.newTreeSet(SummaryComparators.nameComparator());
        Set<EffectorSummary> effectors = Sets.newTreeSet(SummaryComparators.nameComparator());

        for (ConfigKey<?> x: type.getConfigKeys())
            config.add(EntityTransformer.entityConfigSummary(x, typeMap.getConfigKeyField(x.getName())));
        for (Sensor<?> x: type.getSensors())
            sensors.add(SensorTransformer.sensorSummaryForCatalog(x));
        for (Effector<?> x: type.getEffectors())
            effectors.add(EffectorTransformer.effectorSummaryForCatalog(x));

        return new CatalogEntitySummary(item.getId(), item.getName(),
            item.getRegisteredTypeName(), item.getJavaType(), 
            item.getRegisteredTypeName(),
            item.getPlanYaml(),
                item.getDescription(), tidyIconLink(b, item, item.getIconUrl()),
                config, sensors, effectors,
                makeLinks(item));
    }

    public static CatalogItemSummary catalogItemSummary(BrooklynRestResourceUtils b, CatalogItem<?,?> item) {
        return new CatalogItemSummary(item.getId(), item.getName(), 
                item.getRegisteredTypeName(), item.getJavaType(), 
                item.getRegisteredTypeName(),
                item.getPlanYaml(),
                item.getDescription(), tidyIconLink(b, item, item.getIconUrl()), makeLinks(item));
    }

    public static CatalogPolicySummary catalogPolicySummary(BrooklynRestResourceUtils b, CatalogItem<? extends Policy,PolicySpec<?>> item) {
        Set<PolicyConfigSummary> config = ImmutableSet.of();
        return new CatalogPolicySummary(item.getId(), item.getName(), item.getRegisteredTypeName(),
                item.getPlanYaml(),
                item.getDescription(), tidyIconLink(b, item, item.getIconUrl()), config,
                makeLinks(item));
    }

    protected static Map<String, URI> makeLinks(CatalogItem<?,?> item) {
        return MutableMap.<String, URI>of();
    }
    
    private static String tidyIconLink(BrooklynRestResourceUtils b, CatalogItem<?,?> item, String iconUrl) {
        if (b.isUrlServerSideAndSafe(iconUrl))
            return "/v1/catalog/icon/"+item.getId();
        return iconUrl;
    }

}
