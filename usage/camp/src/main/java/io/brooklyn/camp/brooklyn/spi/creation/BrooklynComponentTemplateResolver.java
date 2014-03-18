package io.brooklyn.camp.brooklyn.spi.creation;

import io.brooklyn.camp.brooklyn.BrooklynCampConstants;
import io.brooklyn.camp.spi.AbstractResource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.InternalEntityFactory;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.policy.Enricher;
import brooklyn.policy.EnricherSpec;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.FlagUtils.FlagConfigKeyAndValueRecord;
import brooklyn.util.guava.Maybe;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class BrooklynComponentTemplateResolver {

    private static final Logger log = LoggerFactory.getLogger(BrooklynComponentTemplateResolver.class);
    
    final ManagementContext mgmt;
    final ConfigBag attrs;
    final Maybe<AbstractResource> template;
    AtomicBoolean alreadyBuilt = new AtomicBoolean(false);

    public static class Factory {
        public static BrooklynComponentTemplateResolver newInstance(ManagementContext mgmt, Map<String, Object> childAttrs) {
            return new BrooklynComponentTemplateResolver(mgmt, childAttrs);
        }

        public static BrooklynComponentTemplateResolver newInstance(ManagementContext mgmt, AbstractResource template) {
            return new BrooklynComponentTemplateResolver(mgmt, template);
        }
        
        private static String extractType(ConfigBag attrs) {
            String type;
            type = (String)attrs.getStringKey("serviceType");
            if (type==null) type = (String)attrs.getStringKey("service_type");
            if (type==null) type = (String)attrs.getStringKey("type");
            return type;
        }
    }

    public BrooklynComponentTemplateResolver(ManagementContext mgmt, AbstractResource template) {
        this.mgmt = mgmt;
        this.attrs = ConfigBag.newInstance( template.getCustomAttributes() );
        this.template = Maybe.of(template);
    }
    
    public BrooklynComponentTemplateResolver(ManagementContext mgmt, Map<String, Object> attrs) {
        this.mgmt = mgmt;
        this.attrs = ConfigBag.newInstance( attrs );
        this.template = Maybe.absent();
    }
    
    protected String getJavaType() {
        String type;
        if (template.isPresent()) type = template.get().getType();
        else type = Factory.extractType(attrs);
        if (type==null)
            throw new IllegalStateException("Spec does not declare type: "+this);
        return Strings.removeFromStart(type, "brooklyn:", "java:");
    }

    @SuppressWarnings("unchecked")
    public <T extends Entity> EntitySpec<T> buildSpec() {
        return buildSpec((Class<T>)this.<Entity>loadClass(Entity.class, getJavaType()), null);
    }

    public <T extends Entity> EntitySpec<T> buildSpec(Class<T> type, Class<? extends T> optionalImpl) {
        if (alreadyBuilt.getAndSet(true))
            throw new IllegalStateException("Spec can only be used once: "+this);
        
        EntitySpec<T> spec = EntitySpec.create(type);
        if (optionalImpl != null) spec.impl(optionalImpl);
        
        String name, templateId=null, planId=null;
        if (template.isPresent()) {
            name = template.get().getName();
            templateId = template.get().getId();
        } else {
            name = (String)attrs.getStringKey("name");
        }
        planId = (String)attrs.getStringKey("id");
        if (planId==null)
            planId = (String) attrs.getStringKey(BrooklynCampConstants.PLAN_ID_FLAG);
        
        if (!Strings.isBlank(name))
            spec.displayName(name);
        if (templateId != null)
            spec.configure(BrooklynCampConstants.TEMPLATE_ID, templateId);
        if (planId != null)
            spec.configure(BrooklynCampConstants.PLAN_ID, planId);
        
        List<Location> childLocations = new BrooklynYamlLocationResolver(mgmt).resolveLocations(attrs.getAllConfig(), true);
        if (childLocations != null)
            spec.locations(childLocations);
        
        decorateSpec(spec);
        
        return spec;
    }

    /** Subclass as needed for correct classloading, e.g. OSGi-based resolver (created from osgi:<bundle>: prefix
     * would use that OSGi mechanism here
     */
    @SuppressWarnings("unchecked")
    protected <T> Class<T> loadClass(Class<T> optionalSupertype, String typeName) {
        try {
            if (optionalSupertype!=null && Entity.class.isAssignableFrom(optionalSupertype)) 
                return (Class<T>) BrooklynEntityClassResolver.<Entity>resolveEntity(typeName, mgmt);
            else
                return BrooklynEntityClassResolver.<T>tryLoadFromClasspath(typeName, mgmt).get();
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.warn("Unable to resolve "+typeName+" in spec "+this);
            throw Exceptions.propagate(new IllegalStateException("Unable to resolve "
                + (optionalSupertype!=null ? optionalSupertype.getSimpleName()+" " : "")
                + "type '"+typeName+"'", e));
        }
    }

    protected <T extends Entity> void decorateSpec(EntitySpec<T> spec) {
        spec.policySpecs(extractPolicySpecs());
        spec.enricherSpecs(extractEnricherSpecs());
        configureEntityConfig(spec);
    }

    /** returns new *uninitialised* entity, with just a few of the pieces from the spec;
     * initialisation occurs soon after, in {@link #initEntity(ManagementContext, Entity, EntitySpec)},
     * inside an execution context and after entity ID's are recognised
     */
    protected <T extends Entity> T newEntity(EntitySpec<T> spec) {
        Class<? extends T> entityImpl = (spec.getImplementation() != null) ? spec.getImplementation() : mgmt.getEntityManager().getEntityTypeRegistry().getImplementedBy(spec.getType());
        InternalEntityFactory entityFactory = ((ManagementContextInternal)mgmt).getEntityFactory();
        T entity = entityFactory.constructEntity(entityImpl, spec);
        if (entity instanceof AbstractApplication) {
            FlagUtils.setFieldsFromFlags(ImmutableMap.of("mgmt", mgmt), entity);
        }

        // TODO Some of the code below could go into constructEntity?
        if (spec.getId() != null) {
            FlagUtils.setFieldsFromFlags(ImmutableMap.of("id", spec.getId()), entity);
        }
        String planId = (String)spec.getConfig().get(BrooklynCampConstants.PLAN_ID.getConfigKey());
        if (planId != null) {
            ((EntityInternal)entity).setConfig(BrooklynCampConstants.PLAN_ID, planId);
        }
        ((ManagementContextInternal)mgmt).prePreManage(entity);
        ((AbstractEntity)entity).setManagementContext((ManagementContextInternal)mgmt);
        
        ((AbstractEntity)entity).setProxy(entityFactory.createEntityProxy(spec, entity));
        
        if (spec.getParent() != null) entity.setParent(spec.getParent());
        
        return entity;
    }

    @SuppressWarnings("unchecked")
    private void configureEntityConfig(EntitySpec<?> spec) {
        ConfigBag bag = ConfigBag.newInstance((Map<Object, Object>) attrs.getStringKey("brooklyn.config"));
        
        List<FlagConfigKeyAndValueRecord> records = FlagUtils.findAllFlagsAndConfigKeys(null, spec.getType(), bag);
        Set<String> keyNamesUsed = new LinkedHashSet<String>();
        for (FlagConfigKeyAndValueRecord r: records) {
            if (r.getFlagMaybeValue().isPresent()) {
                Object transformed = transformSpecialFlags(r.getFlagMaybeValue().get(), mgmt);
                spec.configure(r.getFlagName(), transformed);
                keyNamesUsed.add(r.getFlagName());
            }
            if (r.getConfigKeyMaybeValue().isPresent()) {
                Object transformed = transformSpecialFlags(r.getConfigKeyMaybeValue().get(), mgmt);
                spec.configure((ConfigKey<Object>)r.getConfigKey(), transformed);
                keyNamesUsed.add(r.getConfigKey().getName());
            }
        }

        // set unused keys as anonymous config keys -
        // they aren't flags or known config keys, so must be passed as config keys in order for
        // EntitySpec to know what to do with them (as they are passed to the spec as flags)
        for (String key: MutableSet.copyOf(bag.getUnusedConfig().keySet())) {
            // we don't let a flag with the same name as a config key override the config key
            // (that's why we check whether it is used)
            if (!keyNamesUsed.contains(key)) {
                Object transformed = transformSpecialFlags(bag.getStringKey(key), mgmt);
                spec.configure(ConfigKeys.newConfigKey(Object.class, key.toString()), transformed);
            }
        }
    }

    /**
     * Makes additional transformations to the given flag with the extra knowledge of the flag's management context.
     * @return The modified flag, or the flag unchanged.
     */
    protected Object transformSpecialFlags(Object flag, ManagementContext mgmt) {
        if (flag instanceof EntitySpecConfiguration) {
            EntitySpecConfiguration specConfig = (EntitySpecConfiguration) flag;
            return Factory.newInstance(mgmt, specConfig.getSpecConfiguration()).buildSpec();
        }
        return flag;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getChildren(Map<String, Object> attrs) {
        if (attrs==null) return null;
        return (List<Map<String, Object>>) attrs.get("brooklyn.children");
    }


    private <T extends Policy> PolicySpec<?> toCorePolicySpec(Class<T> clazz, Map<?, ?> config) {
        Map<?, ?> policyConfig = (config == null) ? Maps.<Object, Object>newLinkedHashMap() : Maps.newLinkedHashMap(config);
        PolicySpec<?> result;
        result = PolicySpec.create(clazz)
                .configure(policyConfig);
        return result;
    }

    private <T extends Enricher> EnricherSpec<?> toCoreEnricherSpec(Class<T> clazz, Map<?, ?> config) {
        Map<?, ?> enricherConfig = (config == null) ? Maps.<Object, Object>newLinkedHashMap() : Maps.newLinkedHashMap(config);
        EnricherSpec<?> result;
        result = EnricherSpec.create(clazz)
                .configure(enricherConfig);
        return result;
    }
    
    private List<PolicySpec<?>> extractPolicySpecs() {
        return buildPolicySpecs(attrs.getStringKey("brooklyn.policies"));
    }

    private List<PolicySpec<?>> buildPolicySpecs(Object policies) {
        List<PolicySpec<?>> policySpecs = new ArrayList<PolicySpec<?>>();
        if (policies instanceof Iterable) {
            for (Object policy : (Iterable<?>)policies) {
                if (policy instanceof Map) {
                    String policyTypeName = ((Map<?, ?>) policy).get("policyType").toString();
                    Class<? extends Policy> policyType = this.loadClass(Policy.class, policyTypeName);
                    policySpecs.add(toCorePolicySpec(policyType, (Map<?, ?>) ((Map<?, ?>) policy).get("brooklyn.config")));
                } else {
                    throw new IllegalArgumentException("policy should be map, not " + policy.getClass());
                }
            }
        } else if (policies != null) {
            // TODO support a "map" short form (for this, and for others)
            throw new IllegalArgumentException("policies body should be iterable, not " + policies.getClass());
        }
        return policySpecs;
    }
    
    private List<EnricherSpec<?>> extractEnricherSpecs() {
        return buildEnricherSpecs(attrs.getStringKey("brooklyn.enrichers"));
    }    

    private List<EnricherSpec<?>> buildEnricherSpecs(Object enrichers) {
        List<EnricherSpec<?>> enricherSpecs = Lists.newArrayList();
        if (enrichers instanceof Iterable) {
            for (Object enricher : (Iterable<?>)enrichers) {
                if (enricher instanceof Map) {
                    String enricherTypeName = ((Map<?, ?>) enricher).get("enricherType").toString();
                    Class<? extends Enricher> enricherType = this.loadClass(Enricher.class, enricherTypeName);
                    enricherSpecs.add(toCoreEnricherSpec(enricherType, (Map<?, ?>) ((Map<?, ?>) enricher).get("brooklyn.config")));
                } else {
                    throw new IllegalArgumentException("enricher should be map, not " + enricher.getClass());
                }
            }
        } else if (enrichers != null) {
            throw new IllegalArgumentException("enrichers body should be iterable, not " + enrichers.getClass());
        }
        return enricherSpecs;
    }

}
