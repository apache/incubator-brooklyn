package brooklyn.cli.itemlister;

import io.airlift.command.Cli;
import io.airlift.command.Cli.CliBuilder;
import io.airlift.command.Command;
import io.airlift.command.Option;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import brooklyn.basic.BrooklynObject;
import brooklyn.catalog.Catalog;
import brooklyn.cli.AbstractMain;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.location.Location;
import brooklyn.location.LocationResolver;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.util.collections.MutableSet;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class ItemLister extends AbstractMain {

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
        public boolean catalogOnly;

        @Option(name = { "--ignore-impls" }, title = "Ignore Entity implementations, where there is an Entity interface with @ImplementedBy")
        public boolean ignoreImpls;

        @Option(name = { "--headings-only" }, title = "Whether to only show name/type, and not config keys etc")
        public boolean headingsOnly;

        @Override
        public Void call() throws Exception {
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
            
            System.out.println(toJson(result));
            return null;
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
