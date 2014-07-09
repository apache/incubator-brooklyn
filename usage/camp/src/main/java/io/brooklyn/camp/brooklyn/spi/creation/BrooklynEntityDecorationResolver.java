package io.brooklyn.camp.brooklyn.spi.creation;

import io.brooklyn.camp.brooklyn.spi.creation.BrooklynYamlTypeLoader.LoaderFromKey;

import java.util.List;
import java.util.Map;

import brooklyn.entity.proxying.EntityInitializer;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.policy.Enricher;
import brooklyn.policy.EnricherSpec;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;
import brooklyn.util.collections.MutableList;
import brooklyn.util.config.ConfigBag;

import com.google.common.annotations.Beta;

/**
 * Pattern for resolving "decorations" on service specs / entity specs, such as policies, enrichers, etc.
 * @since 0.7.0
 */
@Beta
public abstract class BrooklynEntityDecorationResolver<DT> {

    public final BrooklynYamlTypeLoader.Factory loader;
    
    protected BrooklynEntityDecorationResolver(BrooklynYamlTypeLoader.Factory loader) {
        this.loader = loader;
    }
    
    public abstract void decorate(EntitySpec<?> entitySpec, ConfigBag attrs);

    protected Iterable<? extends DT> buildListOfTheseDecorationsFromEntityAttributes(ConfigBag attrs) {
        Object value = getDecorationAttributeJsonValue(attrs); 
        List<DT> decorations = MutableList.of();
        if (value==null) return decorations;
        if (value instanceof Iterable) {
            for (Object decorationJson: (Iterable<?>)value)
                addDecorationFromJsonMap(checkIsMap(decorationJson), decorations);
        } else {
            // in future may support types other than iterables here, 
            // e.g. a map short form where the key is the type
            throw new IllegalArgumentException(getDecorationKind()+" body should be iterable, not " + value.getClass());
        }
        return decorations;
    }
    
    protected Map<?,?> checkIsMap(Object decorationJson) {
        if (!(decorationJson instanceof Map))
            throw new IllegalArgumentException(getDecorationKind()+" value must be a Map, not " + 
                (decorationJson==null ? null : decorationJson.getClass()) );
        return (Map<?,?>) decorationJson;
    }

    protected abstract String getDecorationKind();
    protected abstract Object getDecorationAttributeJsonValue(ConfigBag attrs);
    
    /** creates and adds decorations from the given json to the given collection; 
     * default impl requires a map and calls {@link #addDecorationFromJsonMap(Map, List)} */
    protected void addDecorationFromJson(Object decorationJson, List<DT> decorations) {
        addDecorationFromJsonMap(checkIsMap(decorationJson), decorations);
    }
    protected abstract void addDecorationFromJsonMap(Map<?,?> decorationJson, List<DT> decorations);
    

    public static class PolicySpecResolver extends BrooklynEntityDecorationResolver<PolicySpec<?>> {
        
        protected PolicySpecResolver(BrooklynYamlTypeLoader.Factory loader) { super(loader); }
        @Override protected String getDecorationKind() { return "Policy"; }

        @Override
        public void decorate(EntitySpec<?> entitySpec, ConfigBag attrs) {
            entitySpec.policySpecs(buildListOfTheseDecorationsFromEntityAttributes(attrs));
        }
        
        @Override
        protected Object getDecorationAttributeJsonValue(ConfigBag attrs) {
            return attrs.getStringKey("brooklyn.policies");
        }

        @Override
        protected void addDecorationFromJsonMap(Map<?, ?> decorationJson, List<PolicySpec<?>> decorations) {
            LoaderFromKey decoLoader = loader.from(decorationJson).prefix("policy");
            // this pattern of creating a spec could be simplified with a "Configurable" superinterface on *Spec  
            decorations.add(PolicySpec.create(decoLoader.getType(Policy.class))
                .configure( decoLoader.getConfigMap() ));
        }
    }

    public static class EnricherSpecResolver extends BrooklynEntityDecorationResolver<EnricherSpec<?>> {
        
        protected EnricherSpecResolver(BrooklynYamlTypeLoader.Factory loader) { super(loader); }
        @Override protected String getDecorationKind() { return "Enricher"; }

        @Override
        public void decorate(EntitySpec<?> entitySpec, ConfigBag attrs) {
            entitySpec.enricherSpecs(buildListOfTheseDecorationsFromEntityAttributes(attrs));
        }
        
        @Override
        protected Object getDecorationAttributeJsonValue(ConfigBag attrs) {
            return attrs.getStringKey("brooklyn.enrichers");
        }

        @Override
        protected void addDecorationFromJsonMap(Map<?, ?> decorationJson, List<EnricherSpec<?>> decorations) {
            LoaderFromKey decoLoader = loader.from(decorationJson).prefix("enricher");
            decorations.add(EnricherSpec.create(decoLoader.getType(Enricher.class))
                .configure( decoLoader.getConfigMap() ));
        }
    }
    
    public static class InitializerResolver extends BrooklynEntityDecorationResolver<EntityInitializer> {
        
        protected InitializerResolver(BrooklynYamlTypeLoader.Factory loader) { super(loader); }
        @Override protected String getDecorationKind() { return "Entity initializer"; }

        @Override
        public void decorate(EntitySpec<?> entitySpec, ConfigBag attrs) {
            entitySpec.addInitializers(buildListOfTheseDecorationsFromEntityAttributes(attrs));
        }
        
        @Override
        protected Object getDecorationAttributeJsonValue(ConfigBag attrs) {
            return attrs.getStringKey("brooklyn.initializers");
        }

        @Override
        protected void addDecorationFromJsonMap(Map<?, ?> decorationJson, List<EntityInitializer> decorations) {
            decorations.add(loader.from(decorationJson).prefix("initializer").newInstance(EntityInitializer.class));
        }
    }
    

}
