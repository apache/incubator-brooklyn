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
package brooklyn.management.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import brooklyn.basic.AbstractBrooklynObjectSpec;
import brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.internal.BasicBrooklynCatalog.BrooklynLoaderTracker;
import brooklyn.catalog.internal.CatalogItemBuilder;
import brooklyn.catalog.internal.CatalogItemDtoAbstract;
import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFunctions;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.management.classloading.JavaBrooklynClassLoadingContext;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.yaml.Yamls;
import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;
import io.brooklyn.camp.spi.pdp.DeploymentPlan;
import io.brooklyn.camp.spi.pdp.Service;

/** Utility methods for working with entities and applications */
public class EntityManagementUtils {

    private static final Logger log = LoggerFactory.getLogger(EntityManagementUtils.class);

    private static final String POLICIES_KEY = "brooklyn.policies";

    /**
     * A marker config value which indicates that an application was created automatically
     * to allow the management of a non-app entity.
     */
    public static final ConfigKey<Boolean> WRAPPER_APP_MARKER = ConfigKeys.newBooleanConfigKey("brooklyn.wrapper_app");

    /** creates an application from the given app spec, managed by the given management context */
    public static <T extends Application> T createUnstarted(ManagementContext mgmt, EntitySpec<T> spec) {
        T app = mgmt.getEntityManager().createEntity(spec);
        Entities.startManagement(app, mgmt);
        return app;
    }

    /** as {@link #createApplication(ManagementContext, EntitySpec)} but for a YAML spec */
    public static Application createUnstarted(ManagementContext mgmt, String yaml) {
        return createUnstarted(mgmt, new StringReader(yaml));
    }

    public static Application createUnstarted(ManagementContext mgmt, Reader yaml) {
        EntitySpec<? extends Application> spec = createEntitySpec(mgmt, yaml);
        return createUnstarted(mgmt, spec);
    }

    public static EntitySpec<? extends Application> createEntitySpec(ManagementContext mgmt, String yaml) {
        return createEntitySpec(mgmt, new StringReader(yaml));
    }

    public static EntitySpec<? extends Application> createEntitySpec(ManagementContext mgmt, Reader yaml) {
        BrooklynClassLoadingContext loader = createDefaultLoader(mgmt);
        return createEntitySpec(mgmt, yaml, loader);
    }

    @SuppressWarnings("unchecked")
    public static EntitySpec<? extends Application> createEntitySpec(ManagementContext mgmt, Reader yaml, BrooklynClassLoadingContext loader) {
        return (EntitySpec<? extends Application>) createSpec(mgmt, yaml, loader);
    }

    private static EntitySpec<? extends Application> createEntitySpec(String symbolicName, CampPlatform camp,
                                                                      DeploymentPlan plan, BrooklynClassLoadingContext loader) {
        AssemblyTemplate at = registerDeploymentPlan(camp, plan, loader);
        AssemblyTemplateInstantiator instantiator = newInstantiator(at);
        if (instantiator instanceof AssemblyTemplateSpecInstantiator) {
            return (EntitySpec<? extends Application>) ((AssemblyTemplateSpecInstantiator)instantiator).createNestedSpec(at, camp, loader, MutableSet.of(symbolicName));
        } else {
            throw new IllegalStateException("Unable to instantiate YAML; incompatible instantiator " + instantiator + " for " + at);
        }
    }

    public static AbstractBrooklynObjectSpec<?, ?> createSpec(ManagementContext mgmt, String yaml, BrooklynClassLoadingContext loader) {
        return createSpec(mgmt, new StringReader(yaml), loader);
    }

    public static AbstractBrooklynObjectSpec<?, ?> createSpec(ManagementContext mgmt, Reader yaml, BrooklynClassLoadingContext loader) {
        Preconditions.checkNotNull(mgmt, "mgmt");
        Preconditions.checkNotNull(mgmt, "yaml");
        Preconditions.checkNotNull(mgmt, "loader");

        CampPlatform camp = getCampPlatform(mgmt);
        DeploymentPlan plan = parseDeploymentPlan(camp, yaml);
        return createSpec(null, mgmt, plan, loader);
    }

    private static AbstractBrooklynObjectSpec<?, ?> createSpec(String symbolicName, ManagementContext mgmt,
                                                               DeploymentPlan plan, BrooklynClassLoadingContext loader) {
        if (isPolicyPlan(plan)) {
            return createPolicySpec(plan, loader);
        } else {
            return createEntitySpec(symbolicName, getCampPlatform(mgmt), plan, loader);
        }
    }

    private static AssemblyTemplateInstantiator newInstantiator(AssemblyTemplate at) {
        try {
            return at.getInstantiator().newInstance();
        } catch (InstantiationException e) {
            throw Exceptions.propagate(e);
        } catch (IllegalAccessException e) {
            throw Exceptions.propagate(e);
        }
    }

    private static AssemblyTemplate registerDeploymentPlan(CampPlatform camp, DeploymentPlan plan, BrooklynClassLoadingContext loader) {
        BrooklynClassLoadingContext prev = BrooklynLoaderTracker.setLoader(loader);
        try {
            return camp.pdp().registerDeploymentPlan(plan);
        } finally {
            BrooklynLoaderTracker.unsetLoader(loader);
            if (prev != null) {
                BrooklynLoaderTracker.setLoader(prev);
            }
        }
    }

    private static boolean isPolicyPlan(DeploymentPlan plan) {
        return plan.getCustomAttributes().containsKey(POLICIES_KEY);
    }

    /** container for operation which creates something and which wants to return both
     * the items created and any pending create/start task */
    public static class CreationResult<T,U> {
        private final T thing;
        @Nullable private final Task<U> task;
        public CreationResult(T thing, Task<U> task) {
            super();
            this.thing = thing;
            this.task = task;
        }
        
        protected static <T,U> CreationResult<T,U> of(T thing, @Nullable Task<U> task) {
            return new CreationResult<T,U>(thing, task);
        }
        
        /** returns the thing/things created */
        @Nullable public T get() { return thing; }
        /** associated task, ie the one doing the creation/starting */
        public Task<U> task() { return task; }
        public CreationResult<T,U> blockUntilComplete(Duration timeout) { if (task!=null) task.blockUntilEnded(timeout); return this; }
        public CreationResult<T,U> blockUntilComplete() { if (task!=null) task.blockUntilEnded(); return this; }
    }

    public static CatalogItemDtoAbstract<?, ?> createCatalogItem(ManagementContext mgmt, String yaml) {
        DeploymentPlan plan = parseDeploymentPlan(getCampPlatform(mgmt), new StringReader(yaml));

        @SuppressWarnings("rawtypes")
        Maybe<Map> possibleCatalog = plan.getCustomAttribute("brooklyn.catalog", Map.class, true);
        MutableMap<String, Object> catalog = MutableMap.of();
        if (possibleCatalog.isPresent()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> catalog2 = (Map<String, Object>) possibleCatalog.get();
            catalog.putAll(catalog2);
        }

        Collection<CatalogItem.CatalogBundle> libraries = Collections.emptyList();
        Maybe<Object> possibleLibraries = catalog.getMaybe("libraries");
        if (possibleLibraries.isAbsent()) possibleLibraries = catalog.getMaybe("brooklyn.libraries");
        if (possibleLibraries.isPresentAndNonNull()) {
            if (!(possibleLibraries.get() instanceof Collection))
                throw new IllegalArgumentException("Libraries should be a list, not "+possibleLibraries.get());
            libraries = CatalogItemDtoAbstract.parseLibraries((Collection<?>) possibleLibraries.get());
        }

        final String id = (String) catalog.getMaybe("id").orNull();
        final String version = Strings.toString(catalog.getMaybe("version").orNull());
        final String symbolicName = (String) catalog.getMaybe("symbolicName").orNull();
        final String name = (String) catalog.getMaybe("name").orNull();
        final String displayName = (String) catalog.getMaybe("displayName").orNull();
        final String description = (String) catalog.getMaybe("description").orNull();
        final String iconUrl = (String) catalog.getMaybe("iconUrl").orNull();
        final String iconUrlUnderscore = (String) catalog.getMaybe("icon_url").orNull();
        final String deprecated = (String) catalog.getMaybe("deprecated").orNull();

        if ((Strings.isNonBlank(id) || Strings.isNonBlank(symbolicName)) &&
                Strings.isNonBlank(displayName) &&
                Strings.isNonBlank(name) && !name.equals(displayName)) {
            log.warn("Name property will be ignored due to the existence of displayName and at least one of id, symbolicName");
        }

        final String catalogSymbolicName;
        if (Strings.isNonBlank(symbolicName)) {
            catalogSymbolicName = symbolicName;
        } else if (Strings.isNonBlank(id)) {
            if (Strings.isNonBlank(id) && CatalogUtils.looksLikeVersionedId(id)) {
                catalogSymbolicName = CatalogUtils.getIdFromVersionedId(id);
            } else {
                catalogSymbolicName = id;
            }
        } else if (Strings.isNonBlank(name)) {
            catalogSymbolicName = name;
        } else if (Strings.isNonBlank(plan.getName())) {
            catalogSymbolicName = plan.getName();
        } else if (plan.getServices().size()==1) {
            Service svc = Iterables.getOnlyElement(plan.getServices());
            if (Strings.isBlank(svc.getServiceType())) {
                throw new IllegalStateException("CAMP service type not expected to be missing for " + svc);
            }
            catalogSymbolicName = svc.getServiceType();
        } else {
            log.error("Can't infer catalog item symbolicName from the following plan:\n" + yaml);
            throw new IllegalStateException("Can't infer catalog item symbolicName from catalog item description");
        }

        final String catalogVersion;
        if (CatalogUtils.looksLikeVersionedId(id)) {
            catalogVersion = CatalogUtils.getVersionFromVersionedId(id);
            if (version != null  && !catalogVersion.equals(version)) {
                throw new IllegalArgumentException("Discrepency between version set in id " + catalogVersion + " and version property " + version);
            }
        } else if (Strings.isNonBlank(version)) {
            catalogVersion = version;
        } else {
            log.warn("No version specified for catalog item " + catalogSymbolicName + ". Using default value.");
            catalogVersion = null;
        }

        final String catalogDisplayName;
        if (Strings.isNonBlank(displayName)) {
            catalogDisplayName = displayName;
        } else if (Strings.isNonBlank(name)) {
            catalogDisplayName = name;
        } else if (Strings.isNonBlank(plan.getName())) {
            catalogDisplayName = plan.getName();
        } else {
            catalogDisplayName = null;
        }

        final String catalogDescription;
        if (Strings.isNonBlank(description)) {
            catalogDescription = description;
        } else if (Strings.isNonBlank(plan.getDescription())) {
            catalogDescription = plan.getDescription();
        } else {
            catalogDescription = null;
        }

        final String catalogIconUrl;
        if (Strings.isNonBlank(iconUrl)) {
            catalogIconUrl = iconUrl;
        } else if (Strings.isNonBlank(iconUrlUnderscore)) {
            catalogIconUrl = iconUrlUnderscore;
        } else {
            catalogIconUrl = null;
        }

        final Boolean catalogDeprecated = Boolean.valueOf(deprecated);

        CatalogUtils.installLibraries(mgmt, libraries);

        String versionedId = CatalogUtils.getVersionedId(catalogSymbolicName, catalogVersion);
        BrooklynClassLoadingContext loader = CatalogUtils.newClassLoadingContext(mgmt, versionedId, libraries);
        AbstractBrooklynObjectSpec<?, ?> spec = createSpec(catalogSymbolicName, mgmt, plan, loader);

        CatalogItemDtoAbstract<?, ?> dto = createItemBuilder(spec, catalogSymbolicName, catalogVersion)
                .libraries(libraries)
                .displayName(catalogDisplayName)
                .description(catalogDescription)
                .iconUrl(catalogIconUrl)
                .plan(yaml)
                .build();

        dto.setManagementContext((ManagementContextInternal) mgmt);
        return dto;
    }

    private static CatalogItemBuilder<?> createItemBuilder(AbstractBrooklynObjectSpec<?, ?> spec, String itemId, String version) {
        if (spec instanceof EntitySpec) {
            if (isApplicationSpec((EntitySpec<?>)spec)) {
                return CatalogItemBuilder.newTemplate(itemId, version);
            } else {
                return CatalogItemBuilder.newEntity(itemId, version);
            }
        } else if (spec instanceof PolicySpec) {
            return CatalogItemBuilder.newPolicy(itemId, version);
        } else {
            throw new IllegalStateException("Unknown spec type " + spec.getClass().getName() + " (" + spec + ")");
        }
    }

    private static boolean isApplicationSpec(EntitySpec<?> spec) {
        return !Boolean.TRUE.equals(spec.getConfig().get(EntityManagementUtils.WRAPPER_APP_MARKER));
    }

    public static Task<Void> start(Application app) {
        return Entities.invokeEffector((EntityLocal) app, app, Startable.START,
                // locations already set in the entities themselves;
                // TODO make it so that this arg does not have to be supplied to START !
                MutableMap.of("locations", MutableList.of()));
    }
    
    public static CreationResult<List<Entity>, List<String>> addChildren(final EntityLocal parent, String yaml, Boolean start) {
        if (Boolean.FALSE.equals(start))
            return CreationResult.of(addChildrenUnstarted(parent, yaml), null);
        return addChildrenStarting(parent, yaml);
    }

    @SuppressWarnings("unchecked")
    private static <T, SpecT> SpecT createPolicySpec(DeploymentPlan plan, BrooklynClassLoadingContext loader) {
        //Would ideally re-use io.brooklyn.camp.brooklyn.spi.creation.BrooklynEntityDecorationResolver.PolicySpecResolver
        //but it is CAMP specific and there is no easy way to get hold of it.
        Object policies = checkNotNull(plan.getCustomAttributes().get(POLICIES_KEY), "policy config");
        if (!(policies instanceof Iterable<?>)) {
            throw new IllegalStateException("The value of " + POLICIES_KEY + " must be an Iterable.");
        }

        Object policy = Iterables.getOnlyElement((Iterable<?>)policies);

        Map<String, Object> policyConfig;
        if (policy instanceof String) {
            policyConfig = ImmutableMap.<String, Object>of("type", policy);
        } else if (policy instanceof Map) {
            policyConfig = (Map<String, Object>) policy;
        } else {
            throw new IllegalStateException("Policy exepcted to be string or map. Unsupported object type " + policy.getClass().getName() + " (" + policy.toString() + ")");
        }

        String policyType = (String) checkNotNull(Yamls.getMultinameAttribute(policyConfig, "policy_type", "policyType", "type"), "policy type");
        Map<String, Object> brooklynConfig = (Map<String, Object>) policyConfig.get("brooklyn.config");
        PolicySpec<? extends Policy> spec = PolicySpec.create(loader.loadClass(policyType, Policy.class));
        if (brooklynConfig != null) {
            spec.configure(brooklynConfig);
        }
        return (SpecT) spec;
    }

    /** adds entities from the given yaml, under the given parent; but does not start them */
    public static List<Entity> addChildrenUnstarted(final EntityLocal parent, String yaml) {
        log.debug("Creating child of "+parent+" from yaml:\n{}", yaml);

        ManagementContext mgmt = parent.getApplication().getManagementContext();
        EntitySpec<?> specA = createEntitySpec(mgmt, yaml);

        // see whether we can promote children
        List<EntitySpec<?>> specs = MutableList.of();
        if (canPromote(specA)) {
            // we can promote
            for (EntitySpec<?> specC: specA.getChildren()) {
                collapseSpec(specA, specC);
                specs.add(specC);
            }
        } else {
            // if not promoting, set a nice name if needed
            if (Strings.isEmpty(specA.getDisplayName())) {
                int size = specA.getChildren().size();
                String childrenCountString = size+" "+(size!=1 ? "children" : "child");
                specA.displayName("Dynamically added "+childrenCountString);
            }
            specs.add(specA);
        }

        final List<Entity> children = MutableList.of();
        for (EntitySpec<?> spec: specs) {
            Entity child = (Entity)parent.addChild(spec);
            Entities.manage(child);
            children.add(child);
        }

        return children;
    }

    private static boolean canPromote(EntitySpec<?> spec) {
        //equivalent to no keys starting with "brooklyn."
        return !isApplicationSpec(spec) &&
                spec.getEnrichers().isEmpty() &&
                spec.getInitializers().isEmpty() &&
                spec.getPolicies().isEmpty();
    }

    public static CreationResult<List<Entity>,List<String>> addChildrenStarting(final EntityLocal parent, String yaml) {
        final List<Entity> children = addChildrenUnstarted(parent, yaml);
        String childrenCountString;

        int size = children.size();
        childrenCountString = size+" "+(size!=1 ? "children" : "child"); 

        TaskBuilder<List<String>> taskM = Tasks.<List<String>>builder().name("add children")
            .dynamic(true)
            .tag(BrooklynTaskTags.NON_TRANSIENT_TASK_TAG)
            .body(new Callable<List<String>>() {
                @Override public List<String> call() throws Exception {
                    return ImmutableList.copyOf(Iterables.transform(children, EntityFunctions.id()));
                }})
                .description("Add and start "+childrenCountString);

        TaskBuilder<?> taskS = Tasks.builder().parallel(true).name("add (parallel)").description("Start each new entity");

        // autostart if requested
        for (Entity child: children) {
            if (child instanceof Startable) {
                taskS.add(Effectors.invocation(child, Startable.START, ImmutableMap.of("locations", ImmutableList.of())));
            } else {
                // include a task, just to give feedback in the GUI
                taskS.add(Tasks.builder().name("create").description("Skipping start (not a Startable Entity)")
                    .body(new Runnable() { public void run() {} })
                    .tag(BrooklynTaskTags.tagForTargetEntity(child))
                    .build());
            }
        }
        taskM.add(taskS.build());
        Task<List<String>> task = Entities.submit(parent, taskM.build());

        return CreationResult.of(children, task);
    }

    /** worker method to combine specs */
    @Beta //where should this live long-term?
    public static void collapseSpec(EntitySpec<?> sourceToBeCollapsed, EntitySpec<?> targetToBeExpanded) {
        if (Strings.isEmpty(targetToBeExpanded.getDisplayName()))
            targetToBeExpanded.displayName(sourceToBeCollapsed.getDisplayName());
        if (!sourceToBeCollapsed.getLocations().isEmpty())
            targetToBeExpanded.locations(sourceToBeCollapsed.getLocations());

        // NB: this clobbers child config; might prefer to deeply merge maps etc
        // (but this should not be surprising, as unwrapping is often parameterising the nested blueprint, so outer config should dominate) 
        targetToBeExpanded.configure(sourceToBeCollapsed.getConfig());
        targetToBeExpanded.configure(sourceToBeCollapsed.getFlags());
        
        // TODO copying tags to all entities is not ideal;
        // in particular the BrooklynTags.YAML_SPEC tag will show all entities if the root has multiple
        targetToBeExpanded.tags(sourceToBeCollapsed.getTags());
    }

    private static JavaBrooklynClassLoadingContext createDefaultLoader(
            ManagementContext mgmt) {
        return JavaBrooklynClassLoadingContext.create(mgmt);
    }

    /** convenience for accessing camp */
    private static CampPlatform getCampPlatform(ManagementContext mgmt) {
        return BrooklynServerConfig.getCampPlatform(mgmt).get();
    }

    private static DeploymentPlan parseDeploymentPlan(CampPlatform camp, Reader yaml) {
        return camp.pdp().parseDeploymentPlan(yaml);
    }
    
}
