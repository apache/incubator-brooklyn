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
package brooklyn.entity.rebind.transformer;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Map;

import brooklyn.entity.rebind.BrooklynObjectType;
import brooklyn.entity.rebind.RebindExceptionHandler;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.transformer.impl.XsltTransformer;
import brooklyn.mementos.BrooklynMementoRawData;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.TemplateProcessor;

import com.google.common.annotations.Beta;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.annotations.VisibleForTesting;

@Beta
public class CompoundTransformer {
    
    public static final CompoundTransformer NOOP = builder().build();
    
    // TODO Does not yet handle BrooklynMementoTransformer, for changing an entire BrooklynMemento.
    // Need to do refactoring in RebindManager and BrooklynMementoPersisterToObjectStore to convert 
    // from a BrooklynMementoRawData to a BrooklynMemento.
    
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Multimap<BrooklynObjectType, RawDataTransformer> rawDataTransformers = ArrayListMultimap.<BrooklynObjectType, RawDataTransformer>create();
        
        public Builder rawDataTransformer(RawDataTransformer val) {
            for (BrooklynObjectType type : BrooklynObjectType.values()) {
                rawDataTransformer(type, val);
            }
            return this;
        }
        public Builder rawDataTransformer(BrooklynObjectType type, RawDataTransformer val) {
            rawDataTransformers.put(checkNotNull(type, "type"), checkNotNull(val, "val"));
            return this;
        }
        public Builder renameType(String oldVal, String newVal) {
            // xstream format for inner classes is like <brooklyn.entity.rebind.transformer.CompoundTransformerTest_-OrigType>
            oldVal = toXstreamClassnameFormat(oldVal);
            newVal = toXstreamClassnameFormat(newVal);
            
            String xsltTemplate = ResourceUtils.create(this).getResourceAsString("classpath://brooklyn/entity/rebind/transformer/renameType.xslt");
            String xslt = TemplateProcessor.processTemplateContents(xsltTemplate, ImmutableMap.of("old_val", oldVal, "new_val", newVal));
            return xsltTransformer(xslt);
        }
        public Builder renameClass(String oldVal, String newVal) {
            // xstream format for inner classes is like <brooklyn.entity.rebind.transformer.CompoundTransformerTest_-OrigType>
            oldVal = toXstreamClassnameFormat(oldVal);
            newVal = toXstreamClassnameFormat(newVal);
            
            String xsltTemplate = ResourceUtils.create(this).getResourceAsString("classpath://brooklyn/entity/rebind/transformer/renameClass.xslt");
            String xslt = TemplateProcessor.processTemplateContents(xsltTemplate, ImmutableMap.of("old_val", oldVal, "new_val", newVal));
            return xsltTransformer(xslt);
        }
        public Builder renameField(String clazz, String oldVal, String newVal) {
            // xstream format for inner classes is like <brooklyn.entity.rebind.transformer.CompoundTransformerTest_-OrigType>
            clazz = toXstreamClassnameFormat(clazz);
            oldVal = toXstreamClassnameFormat(oldVal);
            newVal = toXstreamClassnameFormat(newVal);
            
            String xsltTemplate = ResourceUtils.create(this).getResourceAsString("classpath://brooklyn/entity/rebind/transformer/renameField.xslt");
            String xslt = TemplateProcessor.processTemplateContents(xsltTemplate, ImmutableMap.of("class_name", clazz, "old_val", oldVal, "new_val", newVal));
            return xsltTransformer(xslt);
        }
        /** registers the given XSLT code to be applied to all persisted {@link BrooklynObjectType}s */
        public Builder xsltTransformer(String xslt) {
            XsltTransformer xsltTransformer = new XsltTransformer(xslt);
            for (BrooklynObjectType type : BrooklynObjectType.values()) {
                rawDataTransformer(type, xsltTransformer);
            }
            return this;
        }
        /** registers the given XSLT code to be applied to the given persisted {@link BrooklynObjectType}s */
        public Builder xsltTransformer(BrooklynObjectType type, String xslt) {
            XsltTransformer xsltTransformer = new XsltTransformer(xslt);
            rawDataTransformer(type, xsltTransformer);
            return this;
        }
        private String toXstreamClassnameFormat(String val) {
            return (val.contains("$")) ? val.replace("$", "_-") : val;
        }
        public CompoundTransformer build() {
            return new CompoundTransformer(this);
        }
    }

    private final Multimap<BrooklynObjectType, RawDataTransformer> rawDataTransformers;
    
    protected CompoundTransformer(Builder builder) {
        rawDataTransformers = builder.rawDataTransformers;
    }

    public BrooklynMementoRawData transform(BrooklynMementoPersisterToObjectStore reader, RebindExceptionHandler exceptionHandler) throws Exception {
        BrooklynMementoRawData rawData = reader.loadMementoRawData(exceptionHandler);
        return transform(rawData);
    }
    
    public BrooklynMementoRawData transform(BrooklynMementoRawData rawData) throws Exception {
        Map<String, String> entities = MutableMap.copyOf(rawData.getEntities());
        Map<String, String> locations = MutableMap.copyOf(rawData.getLocations());
        Map<String, String> policies = MutableMap.copyOf(rawData.getPolicies());
        Map<String, String> enrichers = MutableMap.copyOf(rawData.getEnrichers());
        Map<String, String> feeds = MutableMap.copyOf(rawData.getFeeds());
        Map<String, String> catalogItems = MutableMap.copyOf(rawData.getCatalogItems());

        // TODO @neykov asks whether transformers should be run in registration order,
        // rather than in type order.  TBD.  (would be an easy change.)
        // (they're all strings so it shouldn't matter!)
        
        for (BrooklynObjectType type : BrooklynObjectType.values()) {
            Collection<RawDataTransformer> transformers = rawDataTransformers.get(type);
            for (RawDataTransformer transformer : transformers) {
                switch (type) {
                    case ENTITY:
                        for (Map.Entry<String, String> entry : entities.entrySet()) {
                            entry.setValue(transformer.transform(entry.getValue()));
                        }
                        break;
                    case LOCATION:
                        for (Map.Entry<String, String> entry : locations.entrySet()) {
                            entry.setValue(transformer.transform(entry.getValue()));
                        }
                        break;
                    case POLICY:
                        for (Map.Entry<String, String> entry : policies.entrySet()) {
                            entry.setValue(transformer.transform(entry.getValue()));
                        }
                        break;
                    case ENRICHER:
                        for (Map.Entry<String, String> entry : enrichers.entrySet()) {
                            entry.setValue(transformer.transform(entry.getValue()));
                        }
                        break;
                    case FEED:
                        for (Map.Entry<String, String> entry : feeds.entrySet()) {
                            entry.setValue(transformer.transform(entry.getValue()));
                        }
                        break;
                    case CATALOG_ITEM:
                        for (Map.Entry<String, String> entry : catalogItems.entrySet()) {
                            entry.setValue(transformer.transform(entry.getValue()));
                        }
                        break;
                    case UNKNOWN:
                        break; // no-op
                    default:
                        throw new IllegalStateException("Unexpected brooklyn object type "+type);
                    }
            }
        }
        
        return BrooklynMementoRawData.builder()
                .entities(entities)
                .locations(locations)
                .policies(policies)
                .enrichers(enrichers)
                .feeds(feeds)
                .catalogItems(catalogItems)
                .build();
    }
    
    @VisibleForTesting
    Multimap<BrooklynObjectType, RawDataTransformer> getRawDataTransformers() {
        return ArrayListMultimap.create(rawDataTransformers);
    }
}
