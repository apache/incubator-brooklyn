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
package brooklyn.cli.itemlister;

import brooklyn.basic.BrooklynObject;
import brooklyn.catalog.Catalog;
import brooklyn.cli.AbstractMain;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.location.Location;
import brooklyn.location.LocationResolver;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.os.Os;
import brooklyn.util.text.TemplateProcessor;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Charsets;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import io.airlift.command.Cli;
import io.airlift.command.Cli.CliBuilder;
import io.airlift.command.Command;
import io.airlift.command.Option;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemLister extends AbstractMain {
    
    private static final Logger LOG = LoggerFactory.getLogger(ItemLister.class);

    public static void main(String... args) {
        new ItemLister().execCli(args);
    }

    @Command(name = "all", description = "Lists everything")
    public static class ListAllCommand extends BrooklynCommandCollectingArgs {

        @Option(name = { "--jars" }, title = "Jars", description = "jars to scan. If a file (not a url) pointing at a directory, will include all files in that directory")
        public List<String> jars;

        @Option(name = { "--type-regex" }, title = "Regex for types to list")
        public String typeRegex = null;

        @Option(name = { "--catalog-only" }, title = "Whether to only list items annotated with @Catalog")
        public boolean catalogOnly = true;

        @Option(name = { "--ignore-impls" }, title = "Ignore Entity implementations, where there is an Entity interface with @ImplementedBy")
        public boolean ignoreImpls;

        @Option(name = { "--headings-only" }, title = "Whether to only show name/type, and not config keys etc")
        public boolean headingsOnly;
        
        @Option(name = { "--output-folder" }, title = "Whether to only show name/type, and not config keys etc")
        public String outputFolder;

        @SuppressWarnings("unchecked")
        @Override
        public Void call() throws Exception {
            LOG.info("Retrieving objects");
            List<URL> urls = getUrls();
            
            // TODO Remove duplication from separate ListPolicyCommand etc
            List<Class<? extends Entity>> entityTypes = getTypes(urls, Entity.class);
            List<Class<? extends Policy>> policyTypes = getTypes(urls, Policy.class);
            List<Class<? extends Enricher>> enricherTypes = getTypes(urls, Enricher.class);
            List<Class<? extends Location>> locationTypes = getTypes(urls, Location.class, Boolean.FALSE);

            Map<String, Object> result = ImmutableMap.<String, Object>builder()
                    .put("entities", ItemDescriptors.toItemDescriptors(entityTypes, headingsOnly))
                    .put("policies", ItemDescriptors.toItemDescriptors(policyTypes, headingsOnly))
                    .put("enrichers", ItemDescriptors.toItemDescriptors(enricherTypes, headingsOnly))
                    .put("locations", ItemDescriptors.toItemDescriptors(locationTypes, headingsOnly))
                    .put("locationResolvers", ItemDescriptors.toItemDescriptors(ImmutableList.copyOf(ServiceLoader.load(LocationResolver.class))))
                    .build();

            String json = toJson(result);

            if (outputFolder == null) {
                System.out.println(json);
            } else {
                LOG.info("Outputting item list to " + outputFolder);
                String outputPath = Os.mergePaths(outputFolder, "index.html");
                String parentDir = (new File(outputPath).getParentFile()).getAbsolutePath();
                mkdir(parentDir, "entities");
                mkdir(parentDir, "policies");
                mkdir(parentDir, "enrichers");
                mkdir(parentDir, "locations");
                mkdir(parentDir, "locationResolvers");
                Files.write("var items = " + json, new File(Os.mergePaths(outputFolder, "items.js")), Charsets.UTF_8);
                ResourceUtils resourceUtils = ResourceUtils.create(this);
                String css = resourceUtils.getResourceAsString("items.css");
                Files.write(css, new File(Os.mergePaths(outputFolder, "items.css")), Charsets.UTF_8);
                String mainHtml = resourceUtils.getResourceAsString("brooklyn-object-list.html");
                Files.write(mainHtml, new File(Os.mergePaths(outputFolder, "index.html")), Charsets.UTF_8);
                List<Map<String, Object>> entities = (List<Map<String, Object>>) result.get("entities");
                String entityTemplateHtml = resourceUtils.getResourceAsString("entity.html");
                for (Map<String, Object> entity : entities) {
                    String type = (String) entity.get("type");
                    String name = (String) entity.get("name");
                    String entityHtml = TemplateProcessor.processTemplateContents(entityTemplateHtml, ImmutableMap.of("type", type, "name", name));
                    Files.write(entityHtml, new File(Os.mergePaths(outputFolder, "entities", type + ".html")), Charsets.UTF_8);
                }
                List<Map<String, Object>> policies = (List<Map<String, Object>>) result.get("policies");
                String policyTemplateHtml = resourceUtils.getResourceAsString("policy.html");
                for (Map<String, Object> policy : policies) {
                    String type = (String) policy.get("type");
                    String name = (String) policy.get("name");
                    String policyHtml = TemplateProcessor.processTemplateContents(policyTemplateHtml, ImmutableMap.of("type", type, "name", name));
                    Files.write(policyHtml, new File(Os.mergePaths(outputFolder, "policies", type + ".html")), Charsets.UTF_8);
                }
                List<Map<String, Object>> enrichers = (List<Map<String, Object>>) result.get("enrichers");
                String enricherTemplateHtml = resourceUtils.getResourceAsString("enricher.html");
                for (Map<String, Object> enricher : enrichers) {
                    String type = (String) enricher.get("type");
                    String name = (String) enricher.get("name");
                    String enricherHtml = TemplateProcessor.processTemplateContents(enricherTemplateHtml, ImmutableMap.of("type", type, "name", name));
                    Files.write(enricherHtml, new File(Os.mergePaths(outputFolder, "enrichers", type + ".html")), Charsets.UTF_8);
                }
                List<Map<String, Object>> locations = (List<Map<String, Object>>) result.get("locations");
                String locationTemplateHtml = resourceUtils.getResourceAsString("location.html");
                for (Map<String, Object> location : locations) {
                    String type = (String) location.get("type");
                    String locationHtml = TemplateProcessor.processTemplateContents(locationTemplateHtml, ImmutableMap.of("type", type));
                    Files.write(locationHtml, new File(Os.mergePaths(outputFolder, "locations", type + ".html")), Charsets.UTF_8);
                }
                LOG.info("Finished outputting item list to " + outputFolder);
            }
            return null;
        }

        private void mkdir(String rootDir, String dirName) {
            (new File(Os.mergePaths(rootDir, dirName))).mkdirs();
        }

        protected List<URL> getUrls() throws MalformedURLException {
            List<URL> urls = Lists.newArrayList();
            for (String jar : jars) {
                urls.addAll(ClassFinder.toJarUrls(jar));
            }
            return urls;
        }

        private <T extends BrooklynObject> List<Class<? extends T>> getTypes(List<URL> urls, Class<T> type) {
            return getTypes(urls, type, null);
        }
        
        private <T extends BrooklynObject> List<Class<? extends T>> getTypes(List<URL> urls, Class<T> type, Boolean catalogOnlyOverride) {
            FluentIterable<Class<? extends T>> fluent = FluentIterable.from(ClassFinder.findClasses(urls, type));
            if (typeRegex != null) {
                fluent = fluent.filter(ClassFinder.withClassNameMatching(typeRegex));
            }
            if (catalogOnlyOverride == null ? catalogOnly : catalogOnlyOverride) {
                fluent = fluent.filter(ClassFinder.withAnnotation(Catalog.class));
            }
            ImmutableList<Class<? extends T>> result = fluent.toList();
            if (ignoreImpls) {
                MutableSet<Class<? extends T>> mutableResult = MutableSet.copyOf(result);
                for (Class<? extends T> clazz : result) {
                    ImplementedBy implementedBy = clazz.getAnnotation(ImplementedBy.class);
                    if (implementedBy != null) {
                        mutableResult.remove(implementedBy.value());
                    }
                }
                return ImmutableList.copyOf(mutableResult);
            } else {
                return result;
            }
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

    /** method intended for overriding when the script filename is different 
     * @return the name of the script the user has invoked */
    protected String cliScriptName() {
        return "item-lister";
    }

    /** method intended for overriding when a different {@link Cli} is desired,
     * or when the subclass wishes to change any of the arguments */
    protected CliBuilder<BrooklynCommand> cliBuilder() {
        @SuppressWarnings({ "unchecked" })
        CliBuilder<BrooklynCommand> builder = Cli.<BrooklynCommand>builder(cliScriptName())
                .withDescription("Brooklyn Management Service")
                .withDefaultCommand(ListAllCommand.class)
                .withCommands(
                        HelpCommand.class,
                        InfoCommand.class,
                        ListAllCommand.class
                );

        return builder;
    }
}
