package brooklyn.entity.rebind.transformer;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Map;

import brooklyn.entity.rebind.BrooklynObjectType;
import brooklyn.entity.rebind.RebindExceptionHandler;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.transformer.impl.XsltTransformer;
import brooklyn.entity.rebind.transformer.impl.XsltTransformerTest;
import brooklyn.mementos.BrooklynMementoRawData;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.TemplateProcessor;

import com.google.common.annotations.Beta;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

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
            
            String xsltTemplate = ResourceUtils.create(XsltTransformerTest.class).getResourceAsString("classpath://brooklyn/entity/rebind/transformer/renameType.xslt");
            String xslt = TemplateProcessor.processTemplateContents(xsltTemplate, ImmutableMap.of("old_val", oldVal, "new_val", newVal));
            return xsltTransformer(xslt);
        }
        public Builder renameClass(String oldVal, String newVal) {
            // xstream format for inner classes is like <brooklyn.entity.rebind.transformer.CompoundTransfor<merTest_-OrigType>
            oldVal = toXstreamClassnameFormat(oldVal);
            newVal = toXstreamClassnameFormat(newVal);
            
            String xsltTemplate = ResourceUtils.create(XsltTransformerTest.class).getResourceAsString("classpath://brooklyn/entity/rebind/transformer/renameClass.xslt");
            String xslt = TemplateProcessor.processTemplateContents(xsltTemplate, ImmutableMap.of("old_val", oldVal, "new_val", newVal));
            return xsltTransformer(xslt);
        }
        public Builder renameField(String clazz, String oldVal, String newVal) {
            // xstream format for inner classes is like <brooklyn.entity.rebind.transformer.CompoundTransfor<merTest_-OrigType>
            clazz = toXstreamClassnameFormat(clazz);
            oldVal = toXstreamClassnameFormat(oldVal);
            newVal = toXstreamClassnameFormat(newVal);
            
            String xsltTemplate = ResourceUtils.create(XsltTransformerTest.class).getResourceAsString("classpath://brooklyn/entity/rebind/transformer/renameClass.xslt");
            String xslt = TemplateProcessor.processTemplateContents(xsltTemplate, ImmutableMap.of("class_name", clazz, "old_val", oldVal, "new_val", newVal));
            return xsltTransformer(xslt);
        }
        public Builder xsltTransformer(String xslt) {
            XsltTransformer xsltTransformer = new XsltTransformer(xslt);
            for (BrooklynObjectType type : BrooklynObjectType.values()) {
                rawDataTransformer(type, xsltTransformer);
            }
            return this;
        }
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
                .build();
    }
}
