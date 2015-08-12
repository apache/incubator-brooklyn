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
package brooklyn.entity.rebind;

import org.apache.brooklyn.mementos.CatalogItemMemento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.internal.CatalogItemDtoAbstract;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.FlagUtils;

public class BasicCatalogItemRebindSupport extends AbstractBrooklynObjectRebindSupport<CatalogItemMemento> {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(BasicCatalogItemRebindSupport.class);
    
    private final CatalogItemDtoAbstract<?,?> instance;

    public BasicCatalogItemRebindSupport(CatalogItemDtoAbstract<?,?> instance) {
        super(instance);
        this.instance = instance;
    }

    @Override
    public void reconstruct(RebindContext rebindContext, CatalogItemMemento memento) {
        super.reconstruct(rebindContext, memento);
        FlagUtils.setFieldsFromFlags(MutableMap.builder()
                .put("symbolicName", memento.getSymbolicName())
                .put("javaType", memento.getJavaType())
                .put("displayName", memento.getDisplayName())
                .put("description", memento.getDescription())
                .put("iconUrl", memento.getIconUrl())
                .put("version", memento.getVersion())
                .put("libraries", memento.getLibraries())
                .put("planYaml", memento.getPlanYaml())
                .put("deprecated", memento.isDeprecated())
                .build(), instance);
    }

    @Override
    protected void addConfig(RebindContext rebindContext, CatalogItemMemento memento) {
        // no-op
    }

    @Override
    protected void addCustoms(RebindContext rebindContext, CatalogItemMemento memento) {
        // no-op
    }

}
