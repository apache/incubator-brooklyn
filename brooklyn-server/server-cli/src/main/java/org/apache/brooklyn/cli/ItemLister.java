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
package org.apache.brooklyn.cli;

import io.airlift.command.Command;
import io.airlift.command.Option;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.brooklyn.api.catalog.BrooklynCatalog;
import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.objs.SpecParameter;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatform;
import org.apache.brooklyn.camp.spi.PlatformRootSummary;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.rest.domain.EntityConfigSummary;
import org.apache.brooklyn.rest.transform.EntityTransformer;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.stream.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationResolver;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.sensor.Enricher;
import org.apache.brooklyn.cli.lister.ClassFinder;
import org.apache.brooklyn.cli.lister.ItemDescriptors;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.text.TemplateProcessor;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.text.Strings;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

public class ItemLister {
    
    private static final Logger LOG = LoggerFactory.getLogger(ItemLister.class);
    private static final String BASE = "brooklyn/item-lister";
    private static final String BASE_TEMPLATES = BASE+"/"+"templates";
    private static final String BASE_STATICS = BASE+"/"+"statics";

    @Command(name = "list-objects", description = "List Brooklyn objects (Entities, Policies, Enrichers and Locations)")
    public static class ListAllCommand extends AbstractMain.BrooklynCommandCollectingArgs {

        @Option(name = { "--jars" }, title = "Jars", description = "Jars to scan. If a file (not a url) pointing at a directory, will include all files in that directory")
        public List<String> jars = Lists.newLinkedList();

        @Option(name = { "--yaml" }, title = "Yaml", description = "Yaml file to parse. If a file is pointing at a directory, will include all files in that directory.")
        public String yaml = "";

        @Option(name = { "--type-regex" }, title = "Regex for types to list")
        public String typeRegex;

        @Option(name = { "--catalog-only" }, title = "Whether to only list items annotated with @Catalog")
        public boolean catalogOnly = true;

        @Option(name = { "--ignore-impls" }, title = "Ignore Entity implementations, where there is an Entity interface with @ImplementedBy")
        public boolean ignoreImpls = false;

        @Option(name = { "--headings-only" }, title = "Whether to only show name/type, and not config keys etc")
        public boolean headingsOnly = false;
        
        @Option(name = { "--output-folder" }, title = "Folder to save output")
        public String outputFolder;

        /**
         * Used for multiple result when documentation is generated for more than one catalog at time
         */
        private String outputSubFolder;

        @SuppressWarnings("unchecked")
        @Override
        public Void call() throws Exception {
            List<URL> urls = getUrls();
            LOG.info("Retrieving objects from "+urls);

            Map<String, Object> result = MutableMap.of();
            List<String> jsonList = new ArrayList<>();

            if (!jars.isEmpty()) {
                // TODO Remove duplication from separate ListPolicyCommand etc
                List<Class<? extends Entity>> entityTypes = getTypes(urls, Entity.class);
                List<Class<? extends Policy>> policyTypes = getTypes(urls, Policy.class);
                List<Class<? extends Enricher>> enricherTypes = getTypes(urls, Enricher.class);
                List<Class<? extends Location>> locationTypes = getTypes(urls, Location.class, Boolean.FALSE);

                result = ImmutableMap.<String, Object>builder()
                        .put("entities", ItemDescriptors.toItemDescriptors(entityTypes, headingsOnly, "name"))
                        .put("policies", ItemDescriptors.toItemDescriptors(policyTypes, headingsOnly, "name"))
                        .put("enrichers", ItemDescriptors.toItemDescriptors(enricherTypes, headingsOnly, "name"))
                        .put("locations", ItemDescriptors.toItemDescriptors(locationTypes, headingsOnly, "type"))
                        .put("locationResolvers", ItemDescriptors.toItemDescriptors(ImmutableList.copyOf(ServiceLoader.load(LocationResolver.class)), true))
                        .build();
                jsonList.add(toJson(result));
            } else if (!yaml.isEmpty()) {
                LocalManagementContext lmgmt = new LocalManagementContext(BrooklynProperties.Factory.newEmpty());
                BrooklynCampPlatform platform = new BrooklynCampPlatform(
                        PlatformRootSummary.builder().name("Brooklyn CAMP Platform").build(),lmgmt)
                        .setConfigKeyAtManagmentContext();
                BrooklynCatalog catalog = lmgmt.getCatalog();

                for (URL url: urls) {
                    List<Map<?,?>> entities = new ArrayList<>();
                    List<Map<?,?>> policies = new ArrayList<>();
                    List<Map<?,?>> enrichers = new ArrayList<>();
                    List<Map<?,?>> locations = new ArrayList<>();
                    List<Map<?,?>> locationResolvers = new ArrayList<>();

                    String yamlContent = Streams.readFullyString(url.openStream());

                    catalog.addItems(yamlContent);
                    for (CatalogItem item: catalog.getCatalogItems()) {
                        AbstractBrooklynObjectSpec<?,?> spec = catalog.createSpec(item);
                        Map<String,Object> itemDescriptor = ItemDescriptors.toItemDescriptor((Class<? extends BrooklynObject>) spec.getType(), false);
                        List<EntityConfigSummary> config = new ArrayList<>();

                        if (item.getDisplayName() != null) {
                            itemDescriptor.put("name", item.getDisplayName());
                        } else if (!itemDescriptor.containsKey("name") || itemDescriptor.containsKey("name") && itemDescriptor.get("name") == null) {
                            itemDescriptor.put("name", "");
                        }
                        if (!itemDescriptor.containsKey("type") || itemDescriptor.containsKey("type") && itemDescriptor.get("type") == null) {
                            itemDescriptor.put("type", "");
                        }
                        if (item.getDescription() != null) {
                            itemDescriptor.put("description", item.getDescription());
                        }
                        if (item.getIconUrl() != null) {
                            itemDescriptor.put("iconUrl", item.getIconUrl());
                        }

                        double priorityCounter = 0.0d;
                        for (SpecParameter<?> param: spec.getParameters()) {
                            Double priority;
                            if (param.isPinned()) {
                                priority = priorityCounter;
                                priorityCounter++;
                            } else {
                                priority = null;
                            }

                            EntityConfigSummary entityConfigSummary = EntityTransformer.entityConfigSummary(param.getConfigKey(),
                                    param.getLabel(), priority, MutableMap.<String,URI>of());
                            config.add(entityConfigSummary);
                        }
                        itemDescriptor.put("config", config);

                        if (item.getCatalogItemType() == CatalogItem.CatalogItemType.ENTITY || item.getCatalogItemType() == CatalogItem.CatalogItemType.TEMPLATE) {
                            entities.add(itemDescriptor);
                        } else if (item.getCatalogItemType() == CatalogItem.CatalogItemType.POLICY) {
                            policies.add(itemDescriptor);
                        } else if (item.getCatalogItemType() == CatalogItem.CatalogItemType.LOCATION) {
                            locations.add(itemDescriptor);
                        }
                    }
                    for (CatalogItem item: catalog.getCatalogItems()){
                        catalog.deleteCatalogItem(item.getSymbolicName(), item.getVersion());
                    }
                    result = ImmutableMap.<String, Object>builder()
                            .put("entities", entities)
                            .put("policies", policies)
                            .put("enrichers", enrichers)
                            .put("locations", locations)
                            .put("locationResolvers", locationResolvers)
                            .build();

                    jsonList.add(toJson(result));
                }
            }

            int catalogCounter = 0;
            for (String json: jsonList) {

                if (outputFolder == null) {
                    System.out.println(json);
                } else {
                    outputSubFolder = outputFolder;
                    if (jsonList.size() > 1) {
                        catalogCounter++;
                        String subfolder = "catalog"+catalogCounter;
                        outputSubFolder += "/" + subfolder;
                    }
                    LOG.info("Outputting item list (size "+itemCount+") to " + outputSubFolder);
                    String outputPath = Os.mergePaths(outputSubFolder, "index.html");
                    String parentDir = (new File(outputPath).getParentFile()).getAbsolutePath();
                    mkdir(parentDir, "entities");
                    mkdir(parentDir, "policies");
                    mkdir(parentDir, "enrichers");
                    mkdir(parentDir, "locations");
                    mkdir(parentDir, "locationResolvers"); //TODO nothing written here yet...

                    mkdir(parentDir, "style");
                    mkdir(Os.mergePaths(parentDir, "style"), "js");
                    mkdir(Os.mergePaths(parentDir, "style", "js"), "catalog");

                    Files.write("var items = " + json, new File(Os.mergePaths(outputSubFolder, "items.js")), Charsets.UTF_8);
                    Files.write(json, new File(Os.mergePaths(outputSubFolder, "items.json")), Charsets.UTF_8);
                    ResourceUtils resourceUtils = ResourceUtils.create(this);

                    // root - just loads the above JSON
                    copyFromItemListerClasspathBaseStaticsToOutputDir(resourceUtils, "brooklyn-object-list.html", "index.html");

                    // statics - structure mirrors docs (not for any real reason however... the json is usually enough for our docs)
                    copyFromItemListerClasspathBaseStaticsToOutputDir(resourceUtils, "common.js");
                    copyFromItemListerClasspathBaseStaticsToOutputDir(resourceUtils, "items.css");
                    copyFromItemListerClasspathBaseStaticsToOutputDir(resourceUtils, "style/js/underscore-min.js");
                    copyFromItemListerClasspathBaseStaticsToOutputDir(resourceUtils, "style/js/underscore-min.map");
                    copyFromItemListerClasspathBaseStaticsToOutputDir(resourceUtils, "style/js/catalog/typeahead.js");

                    // now make pages for each item

                    List<Map<String, Object>> entities = (List<Map<String, Object>>) result.get("entities");
                    String entityTemplateHtml = resourceUtils.getResourceAsString(Urls.mergePaths(BASE_TEMPLATES, "entity.html"));
                    for (Map<String, Object> entity : entities) {
                        String type = (String) entity.get("type");
                        String name = (String) entity.get("name");
                        String entityHtml = TemplateProcessor.processTemplateContents(entityTemplateHtml, ImmutableMap.of("type", type, "name", name));
                        Files.write(entityHtml, new File(Os.mergePaths(outputSubFolder, "entities", type + ".html")), Charsets.UTF_8);
                    }

                    List<Map<String, Object>> policies = (List<Map<String, Object>>) result.get("policies");
                    String policyTemplateHtml = resourceUtils.getResourceAsString(Urls.mergePaths(BASE_TEMPLATES, "policy.html"));
                    for (Map<String, Object> policy : policies) {
                        String type = (String) policy.get("type");
                        String name = (String) policy.get("name");
                        String policyHtml = TemplateProcessor.processTemplateContents(policyTemplateHtml, ImmutableMap.of("type", type, "name", name));
                        Files.write(policyHtml, new File(Os.mergePaths(outputSubFolder, "policies", type + ".html")), Charsets.UTF_8);
                    }

                    List<Map<String, Object>> enrichers = (List<Map<String, Object>>) result.get("enrichers");
                    String enricherTemplateHtml = resourceUtils.getResourceAsString(Urls.mergePaths(BASE_TEMPLATES, "enricher.html"));
                    for (Map<String, Object> enricher : enrichers) {
                        String type = (String) enricher.get("type");
                        String name = (String) enricher.get("name");
                        String enricherHtml = TemplateProcessor.processTemplateContents(enricherTemplateHtml, ImmutableMap.of("type", type, "name", name));
                        Files.write(enricherHtml, new File(Os.mergePaths(outputSubFolder, "enrichers", type + ".html")), Charsets.UTF_8);
                    }

                    List<Map<String, Object>> locations = (List<Map<String, Object>>) result.get("locations");
                    String locationTemplateHtml = resourceUtils.getResourceAsString(Urls.mergePaths(BASE_TEMPLATES, "location.html"));
                    for (Map<String, Object> location : locations) {
                        String type = (String) location.get("type");
                        String locationHtml = TemplateProcessor.processTemplateContents(locationTemplateHtml, ImmutableMap.of("type", type));
                        Files.write(locationHtml, new File(Os.mergePaths(outputSubFolder, "locations", type + ".html")), Charsets.UTF_8);
                    }
                    LOG.info("Finished outputting item list to " + outputSubFolder);
                }
            }
            return null;
        }

        private void copyFromItemListerClasspathBaseStaticsToOutputDir(ResourceUtils resourceUtils, String item) throws IOException {
            copyFromItemListerClasspathBaseStaticsToOutputDir(resourceUtils, item, item);
        }
        private void copyFromItemListerClasspathBaseStaticsToOutputDir(ResourceUtils resourceUtils, String item, String dest) throws IOException {
            String js = resourceUtils.getResourceAsString(Urls.mergePaths(BASE_STATICS, item));
            Files.write(js, new File(Os.mergePaths(outputSubFolder, dest)), Charsets.UTF_8);
        }

        private void mkdir(String rootDir, String dirName) {
            (new File(Os.mergePaths(rootDir, dirName))).mkdirs();
        }

        protected List<URL> getUrls() throws MalformedURLException, IOException {
            List<URL> urls = Lists.newArrayList();
            if (jars.isEmpty()) {
                if (!yaml.isEmpty()) {
                    File yamlFolder = new File(yaml);
                    if (yamlFolder.isDirectory()) {
                        File[] fileList = yamlFolder.listFiles();

                        for (File file : fileList) {
                            if (file.isFile() && (file.getName().contains(".yaml") || file.getName().contains(".bom"))) {
                                urls.add(file.toURI().toURL());
                            }
                        }
                    } else {
                        urls.add(new File(yaml).toURI().toURL());
                    }
                } else {
                    String classpath = System.getenv("INITIAL_CLASSPATH");
                    if (Strings.isNonBlank(classpath)) {
                        List<String> entries = Splitter.on(":").omitEmptyStrings().trimResults().splitToList(classpath);
                        for (String entry : entries) {
                            if (entry.endsWith(".jar") || entry.endsWith("/*")) {
                                urls.addAll(ClassFinder.toJarUrls(entry.replace("/*", "")));
                            }
                        }
                    } else {
                        throw new IllegalArgumentException("No Jars to process");
                    }
                }
            } else {
                for (String jar : jars) {
                    List<URL> expanded = ClassFinder.toJarUrls(jar);
                    if (expanded.isEmpty())
                        LOG.warn("No jars found at: "+jar);
                    urls.addAll(expanded);
                }
            }
            return urls;
        }

        private <T extends BrooklynObject> List<Class<? extends T>> getTypes(List<URL> urls, Class<T> type) {
            return getTypes(urls, type, null);
        }

        int itemCount = 0;
        
        private <T extends BrooklynObject> List<Class<? extends T>> getTypes(List<URL> urls, Class<T> type, Boolean catalogOnlyOverride) {
            FluentIterable<Class<? extends T>> fluent = FluentIterable.from(ClassFinder.findClasses(urls, type));
            if (typeRegex != null) {
                fluent = fluent.filter(ClassFinder.withClassNameMatching(typeRegex));
            }
            if (catalogOnlyOverride == null ? catalogOnly : catalogOnlyOverride) {
                fluent = fluent.filter(ClassFinder.withAnnotation(Catalog.class));
            }
            List<Class<? extends T>> filtered = fluent.toList();
            Collection<Class<? extends T>> result;
            if (ignoreImpls) {
                result = MutableSet.copyOf(filtered);
                for (Class<? extends T> clazz : filtered) {
                    ImplementedBy implementedBy = clazz.getAnnotation(ImplementedBy.class);
                    if (implementedBy != null) {
                        result.remove(implementedBy.value());
                    }
                }
            } else {
                result = filtered;
            }
            itemCount += result.size();
            return ImmutableList.copyOf(result);
        }
        
        private String toJson(Object obj) throws JsonProcessingException {
            ObjectMapper objectMapper = new ObjectMapper()
                    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .enable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS)
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                    .setSerializationInclusion(JsonInclude.Include.ALWAYS)
            
                    // Only serialise annotated fields
                    .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                    .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
            
            return objectMapper.writeValueAsString(obj);
        }
    }
}
