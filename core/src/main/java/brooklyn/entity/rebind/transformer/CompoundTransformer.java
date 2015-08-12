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

import org.apache.brooklyn.mementos.BrooklynMementoRawData;

import brooklyn.entity.rebind.BrooklynObjectType;
import brooklyn.entity.rebind.RebindExceptionHandler;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.transformer.impl.XsltTransformer;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.Strings;
import brooklyn.util.text.TemplateProcessor;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
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
        protected Builder xsltTransformerFromXsltFreemarkerTemplateUrl(String templateUrl, Map<String,String> vars) {
            String xsltTemplate = ResourceUtils.create(this).getResourceAsString(templateUrl);
            String xslt = TemplateProcessor.processTemplateContents(xsltTemplate, vars);
            return xsltTransformer(xslt);
        }
        protected Builder xsltTransformerRecursiveCopyWithExtraRules(String ...rules) {
            String xsltTemplate = ResourceUtils.create(this).getResourceAsString("classpath://brooklyn/entity/rebind/transformer/recursiveCopyWithExtraRules.xslt");
            String xslt = TemplateProcessor.processTemplateContents(xsltTemplate, ImmutableMap.of("extra_rules", Strings.join(rules, "\n")));
            return xsltTransformer(xslt);
        }

        /** Discards and replaces the item at the given XPath.
         * <p>
         * For example to replace all occurrences 
         * of text "foo" inside a tag "Tag1", you can use <code>TagName/text()[.='foo']</code>;
         * passing <code>bar</code> as the second argument would cause 
         * <code>&lt;Tag1&gt;foo&lt;/Tag1&gt;</code> to become <code>&lt;Tag1&gt;bar&lt;/Tag1&gt;</code>.
         * <p>
         * Note that java class names may require conversion prior to invoking this;
         * see {@link #toXstreamClassnameFormat(String)}. 
         */
        // ie TagName/text()[.='foo'] with 'bar' causes <Tag1>foo</Tag1> to <Tag1>bar</Tag1>
        public Builder xmlReplaceItem(String xpathToMatch, String newValue) {
            return xsltTransformerRecursiveCopyWithExtraRules(
                "<xsl:template match=\""+xpathToMatch+"\">"
                    + newValue
                + "</xsl:template>");
        }
        
        /** 
         * Replaces a tag, but while continuing to recurse.
         */
        public Builder xmlRenameTag(String xpathToMatch, String newValue) {
            return xmlReplaceItem(xpathToMatch,
                "<"+newValue+">"
                    + "<xsl:apply-templates select=\"@*|node()\" />"
                + "</"+newValue+">"); 
        }
        
        public Builder xmlChangeAttribute(String xpathToMatch, String newValue) {
            return xmlReplaceItem(xpathToMatch,
                "<xsl:attribute name='{local-name()}'>" +
                newValue +
                "</xsl:attribute>");
        }

        /** 
         * Renames an explicit type name reference in brooklyn-xstream serialization.
         * <p>
         * Really this changes the contents inside any tag named "type",
         * where the contents match the oldVal, they are changed to the newVal.
         * <p> 
         * In brooklyn-xstream, the "type" node typically gives the name of a java or catalog type to be used
         * when creating an instance; that's how this works. 
         */
        public Builder renameType(String oldVal, String newVal) {
            return xmlReplaceItem("type/text()[.='"+toXstreamClassnameFormat(oldVal)+"']", toXstreamClassnameFormat(newVal));
            // previously this did a more complex looping, essentially
            // <when .=oldVal>newVal</when><otherwise><apply-templates/></otherwise>
            // but i think these are equivalent
        }
        /** 
         * Renames an implicit class name reference (a tag).
         * <p>
         * Really this changes any XML tag matching a given old value;
         * the tag is changed to the new value.
         * <p>
         * In brooklyn-xstream many tags correspond to the java class of an object;
         * that's how this works to to change the java class (or xstream alias) 
         * of a persisted instance, included nested instances. 
         */
        public Builder renameClassTag(String oldVal, String newVal) {
            return xmlRenameTag(toXstreamClassnameFormat(oldVal), toXstreamClassnameFormat(newVal));
        }
        /** 
         * Renames a field in xstream serialization.
         * <p>
         * Really this changes an XML tag inside another tag, 
         * where the outer tag and inner tag match the clazz and oldVal values given here,
         * the inner tag is changed to the newVal.
         * <p>
         * In brooklyn-xstream, tags corresponding to fields are contained in the tag 
         * corresponding to the class name; that's how this works.
         */
        public Builder renameField(String clazz, String oldVal, String newVal) {
            return xmlRenameTag(toXstreamClassnameFormat(clazz)+"/"+toXstreamClassnameFormat(oldVal), toXstreamClassnameFormat(newVal));
        }
        /** Changes the contents of an XML tag 'catalogItemId' where the
         * old text matches oldSymbolicName and optionally oldVersion
         * to have newSymbolicName and newVersion. 
         * <p>
         * This provides a programmatic way to change the catalogItemID. */
        public Builder changeCatalogItemId(String oldSymbolicName, String oldVersion,
                String newSymbolicName, String newVersion) {
            if (oldVersion==null)
                return changeCatalogItemId(oldSymbolicName, newSymbolicName, newVersion);
            // warnings use underscore notation because that's what CompoundTransformerLoader uses
            return xmlReplaceItem("catalogItemId/text()[.='"+
                Preconditions.checkNotNull(oldSymbolicName, "old_symbolic_name")+":"+Preconditions.checkNotNull(oldVersion, "old_version")+"']", 
                Preconditions.checkNotNull(newSymbolicName, "new_symbolic_name")+":"+Preconditions.checkNotNull(newVersion, "new_version"));
        }
        /** As {@link #changeCatalogItemId(String, String, String, String)} matching any old version. */
        public Builder changeCatalogItemId(String oldSymbolicName, String newSymbolicName, String newVersion) {
            return xmlReplaceItem("catalogItemId/text()[starts-with(.,'"+Preconditions.checkNotNull(oldSymbolicName, "old_symbolic_name")+":')]", 
                Preconditions.checkNotNull(newSymbolicName, "new_symbolic_name")+":"+Preconditions.checkNotNull(newVersion, "new_version"));
        }

        /**
         * Updates all references to a class to a new value
         * @param oldName the old name of the class
         * @param newName the new name of the class to be used instead
         */
        public Builder renameClass(String oldName, String newName) {
            return renameClassTag(oldName, newName)
                .xmlChangeAttribute("//@class[.='" + oldName + "']", newName)
                .renameType(oldName, newName);
                //TODO update reference attributes
        }

        private String toXstreamClassnameFormat(String val) {
            // xstream format for inner classes is like <brooklyn.entity.rebind.transformer.CompoundTransformerTest_-OrigType>
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
