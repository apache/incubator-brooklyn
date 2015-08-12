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
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.BrooklynObject;

import org.apache.brooklyn.catalog.Catalog;
import org.apache.brooklyn.cli.lister.ClassFinder;
import org.apache.brooklyn.cli.lister.ItemDescriptors;
import org.apache.brooklyn.policy.Enricher;
import org.apache.brooklyn.policy.Policy;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.location.Location;
import brooklyn.location.LocationResolver;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.net.Urls;
import brooklyn.util.os.Os;
import brooklyn.util.text.Strings;
import brooklyn.util.text.TemplateProcessor;

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

        @SuppressWarnings("unchecked")
        @Override
        public Void call() throws Exception {
            List<URL> urls = getUrls();
            LOG.info("Retrieving objects from "+urls);

            // TODO Remove duplication from separate ListPolicyCommand etc
            List<Class<? extends Entity>> entityTypes = getTypes(urls, Entity.class);
            List<Class<? extends Policy>> policyTypes = getTypes(urls, Policy.class);
            List<Class<? extends Enricher>> enricherTypes = getTypes(urls, Enricher.class);
            List<Class<? extends Location>> locationTypes = getTypes(urls, Location.class, Boolean.FALSE);

            Map<String, Object> result = ImmutableMap.<String, Object>builder()
                    .put("entities", ItemDescriptors.toItemDescriptors(entityTypes, headingsOnly, "name"))
                    .put("policies", ItemDescriptors.toItemDescriptors(policyTypes, headingsOnly, "name"))
                    .put("enrichers", ItemDescriptors.toItemDescriptors(enricherTypes, headingsOnly, "name"))
                    .put("locations", ItemDescriptors.toItemDescriptors(locationTypes, headingsOnly, "type"))
                    .put("locationResolvers", ItemDescriptors.toItemDescriptors(ImmutableList.copyOf(ServiceLoader.load(LocationResolver.class)), true))
                    .build();

            String json = toJson(result);

            if (outputFolder == null) {
                System.out.println(json);
            } else {
                LOG.info("Outputting item list (size "+itemCount+") to " + outputFolder);
                String outputPath = Os.mergePaths(outputFolder, "index.html");
                String parentDir = (new File(outputPath).getParentFile()).getAbsolutePath();
                mkdir(parentDir, "entities");
                mkdir(parentDir, "policies");
                mkdir(parentDir, "enrichers");
                mkdir(parentDir, "locations");
                mkdir(parentDir, "locationResolvers"); //TODO nothing written here yet...
                
                mkdir(parentDir, "style");
                mkdir(Os.mergePaths(parentDir, "style"), "js");
                mkdir(Os.mergePaths(parentDir, "style", "js"), "catalog");
                
                Files.write("var items = " + json, new File(Os.mergePaths(outputFolder, "items.js")), Charsets.UTF_8);
                ResourceUtils resourceUtils = ResourceUtils.create(this);
                
                // root - just loads the above JSON
                copyFromItemListerClasspathBaseStaticsToOutputDir(resourceUtils, "brooklyn-object-list.html", "index.html");
                
                // statics - structure mirrors docs (not for any real reason however... the json is usually enough for our docs)
                copyFromItemListerClasspathBaseStaticsToOutputDir(resourceUtils, "common.js");
                copyFromItemListerClasspathBaseStaticsToOutputDir(resourceUtils, "items.css");
                copyFromItemListerClasspathBaseStaticsToOutputDir(resourceUtils, "style/js/underscore-min.js");
                copyFromItemListerClasspathBaseStaticsToOutputDir(resourceUtils, "style/js/underscore-min.map");
                copyFromItemListerClasspathBaseStaticsToOutputDir(resourceUtils, "style/js/catalog/bloodhound.js");

                // now make pages for each item
                
                List<Map<String, Object>> entities = (List<Map<String, Object>>) result.get("entities");
                String entityTemplateHtml = resourceUtils.getResourceAsString(Urls.mergePaths(BASE_TEMPLATES, "entity.html"));
                for (Map<String, Object> entity : entities) {
                    String type = (String) entity.get("type");
                    String name = (String) entity.get("name");
                    String entityHtml = TemplateProcessor.processTemplateContents(entityTemplateHtml, ImmutableMap.of("type", type, "name", name));
                    Files.write(entityHtml, new File(Os.mergePaths(outputFolder, "entities", type + ".html")), Charsets.UTF_8);
                }
                
                List<Map<String, Object>> policies = (List<Map<String, Object>>) result.get("policies");
                String policyTemplateHtml = resourceUtils.getResourceAsString(Urls.mergePaths(BASE_TEMPLATES, "policy.html"));
                for (Map<String, Object> policy : policies) {
                    String type = (String) policy.get("type");
                    String name = (String) policy.get("name");
                    String policyHtml = TemplateProcessor.processTemplateContents(policyTemplateHtml, ImmutableMap.of("type", type, "name", name));
                    Files.write(policyHtml, new File(Os.mergePaths(outputFolder, "policies", type + ".html")), Charsets.UTF_8);
                }
                
                List<Map<String, Object>> enrichers = (List<Map<String, Object>>) result.get("enrichers");
                String enricherTemplateHtml = resourceUtils.getResourceAsString(Urls.mergePaths(BASE_TEMPLATES, "enricher.html"));
                for (Map<String, Object> enricher : enrichers) {
                    String type = (String) enricher.get("type");
                    String name = (String) enricher.get("name");
                    String enricherHtml = TemplateProcessor.processTemplateContents(enricherTemplateHtml, ImmutableMap.of("type", type, "name", name));
                    Files.write(enricherHtml, new File(Os.mergePaths(outputFolder, "enrichers", type + ".html")), Charsets.UTF_8);
                }
                
                List<Map<String, Object>> locations = (List<Map<String, Object>>) result.get("locations");
                String locationTemplateHtml = resourceUtils.getResourceAsString(Urls.mergePaths(BASE_TEMPLATES, "location.html"));
                for (Map<String, Object> location : locations) {
                    String type = (String) location.get("type");
                    String locationHtml = TemplateProcessor.processTemplateContents(locationTemplateHtml, ImmutableMap.of("type", type));
                    Files.write(locationHtml, new File(Os.mergePaths(outputFolder, "locations", type + ".html")), Charsets.UTF_8);
                }
                LOG.info("Finished outputting item list to " + outputFolder);
            }
            return null;
        }

        private void copyFromItemListerClasspathBaseStaticsToOutputDir(ResourceUtils resourceUtils, String item) throws IOException {
            copyFromItemListerClasspathBaseStaticsToOutputDir(resourceUtils, item, item);
        }
        private void copyFromItemListerClasspathBaseStaticsToOutputDir(ResourceUtils resourceUtils, String item, String dest) throws IOException {
            String js = resourceUtils.getResourceAsString(Urls.mergePaths(BASE_STATICS, item));
            Files.write(js, new File(Os.mergePaths(outputFolder, dest)), Charsets.UTF_8);
        }

        private void mkdir(String rootDir, String dirName) {
            (new File(Os.mergePaths(rootDir, dirName))).mkdirs();
        }

        protected List<URL> getUrls() throws MalformedURLException {
            List<URL> urls = Lists.newArrayList();
            if (jars.isEmpty()) {
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
