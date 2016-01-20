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
package org.apache.brooklyn.core.mgmt;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.catalog.internal.BasicBrooklynCatalog;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityFunctions;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.typereg.RegisteredTypeLoadingContexts;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.task.TaskBuilder;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Runnables;

/** Utility methods for working with entities and applications */
public class EntityManagementUtils {

    private static final Logger log = LoggerFactory.getLogger(EntityManagementUtils.class);

    /**
     * A marker config value which indicates that an {@link Application} entity was created automatically,
     * needed because a plan might give multiple top-level entities or a non-Application top-level entity,
     * in a context where Brooklyn requires an {@link Application} at the root.
     * <p>
     * Typically when such a wrapper app wraps another {@link Application}
     * (or where we are looking for a single {@link Entity}, or a list to add, and they are so wrapped)
     * it will be unwrapped. 
     * See {@link #newWrapperApp()} and {@link #unwrapApplication(EntitySpec)}.
     */
    public static final ConfigKey<Boolean> WRAPPER_APP_MARKER = ConfigKeys.newBooleanConfigKey("brooklyn.wrapper_app");

    /** creates an application from the given app spec, managed by the given management context */
    public static <T extends Application> T createUnstarted(ManagementContext mgmt, EntitySpec<T> spec) {
        T app = mgmt.getEntityManager().createEntity(spec);
        return app;
    }

    /** as {@link #createUnstarted(ManagementContext, EntitySpec)} but for a string plan (e.g. camp yaml) */
    public static Application createUnstarted(ManagementContext mgmt, String plan) {
        EntitySpec<? extends Application> spec = createEntitySpecForApplication(mgmt, plan);
        return createUnstarted(mgmt, spec);
    }
    
    @SuppressWarnings("unchecked")
    public static EntitySpec<? extends Application> createEntitySpecForApplication(ManagementContext mgmt, final String plan) {
        return mgmt.getTypeRegistry().createSpecFromPlan(null, plan, RegisteredTypeLoadingContexts.spec(Application.class), EntitySpec.class);
    }

    @Deprecated /** @deprecated since 0.9.0; use {@link BrooklynTypeRegistry#createSpec(RegisteredType, org.apache.brooklyn.api.typereg.RegisteredTypeConstraint, Class)} */
    // not used in Brooklyn
    public static <T,SpecT extends AbstractBrooklynObjectSpec<? extends T, SpecT>> SpecT createCatalogSpec(ManagementContext mgmt, CatalogItem<T, SpecT> item) {
        return createCatalogSpec(mgmt, item, ImmutableSet.<String>of());
    }

    @Deprecated /** @deprecated since 0.9.0; use {@link BrooklynTypeRegistry#createSpec(RegisteredType, org.apache.brooklyn.api.typereg.RegisteredTypeConstraint, Class)} */
    // not used in Brooklyn
    public static <T,SpecT extends AbstractBrooklynObjectSpec<? extends T, SpecT>> SpecT createCatalogSpec(ManagementContext mgmt, final CatalogItem<T, SpecT> item, final Set<String> encounteredTypes) {
        return BasicBrooklynCatalog.internalCreateSpecLegacy(mgmt, item, encounteredTypes, true);
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

    public static <T extends Application> CreationResult<T,Void> createStarting(ManagementContext mgmt, EntitySpec<T> appSpec) {
        return start(createUnstarted(mgmt, appSpec));
    }

    public static CreationResult<? extends Application,Void> createStarting(ManagementContext mgmt, String appSpec) {
        return start(createUnstarted(mgmt, appSpec));
    }

    public static <T extends Application> CreationResult<T,Void> start(T app) {
        Task<Void> task = Entities.invokeEffector(app, app, Startable.START,
            // locations already set in the entities themselves;
            // TODO make it so that this arg does not have to be supplied to START !
            MutableMap.of("locations", MutableList.of()));
        return CreationResult.of(app, task);
    }
    
    public static CreationResult<List<Entity>, List<String>> addChildren(final Entity parent, String yaml, Boolean start) {
        if (Boolean.FALSE.equals(start))
            return CreationResult.of(addChildrenUnstarted(parent, yaml), null);
        return addChildrenStarting(parent, yaml);
    }
    
    /** adds entities from the given yaml, under the given parent; but does not start them */
    public static List<Entity> addChildrenUnstarted(final Entity parent, String yaml) {
        log.debug("Creating child of "+parent+" from yaml:\n{}", yaml);

        ManagementContext mgmt = parent.getApplication().getManagementContext();

        EntitySpec<? extends Application> specA = createEntitySpecForApplication(mgmt, yaml);

        // see whether we can promote children
        List<EntitySpec<?>> specs = MutableList.of();
        if (!canUnwrapEntity(specA)) {
            // if not promoting, set a nice name if needed
            if (Strings.isEmpty(specA.getDisplayName())) {
                int size = specA.getChildren().size();
                String childrenCountString = size+" "+(size!=1 ? "children" : "child");
                specA.displayName("Dynamically added "+childrenCountString);
            }
        }
        
        specs.add(unwrapEntity(specA));

        final List<Entity> children = MutableList.of();
        for (EntitySpec<?> spec: specs) {
            Entity child = (Entity)parent.addChild(spec);
            children.add(child);
        }

        return children;
    }

    public static CreationResult<List<Entity>,List<String>> addChildrenStarting(final Entity parent, String yaml) {
        final List<Entity> children = addChildrenUnstarted(parent, yaml);
        String childrenCountString;

        int size = children.size();
        childrenCountString = size+" "+(size!=1 ? "children" : "child"); 

        TaskBuilder<List<String>> taskM = Tasks.<List<String>>builder().displayName("add children")
            .dynamic(true)
            .tag(BrooklynTaskTags.NON_TRANSIENT_TASK_TAG)
            .body(new Callable<List<String>>() {
                @Override public List<String> call() throws Exception {
                    return ImmutableList.copyOf(Iterables.transform(children, EntityFunctions.id()));
                }})
                .description("Add and start "+childrenCountString);

        TaskBuilder<?> taskS = Tasks.builder().parallel(true).displayName("add (parallel)").description("Start each new entity");

        // autostart if requested
        for (Entity child: children) {
            if (child instanceof Startable) {
                taskS.add(Effectors.invocation(child, Startable.START, ImmutableMap.of("locations", ImmutableList.of())));
            } else {
                // include a task, just to give feedback in the GUI
                taskS.add(Tasks.builder().displayName("create").description("Skipping start (not a Startable Entity)")
                    .body(Runnables.doNothing())
                    .tag(BrooklynTaskTags.tagForTargetEntity(child))
                    .build());
            }
        }
        taskM.add(taskS.build());
        Task<List<String>> task = Entities.submit(parent, taskM.build());

        return CreationResult.of(children, task);
    }
    
    /** Unwraps a single {@link Entity} if appropriate. See {@link #WRAPPER_APP_MARKER}.
     * Also see {@link #canUnwrapEntity(EntitySpec)} to test whether it will unwrap. */
    public static EntitySpec<? extends Entity> unwrapEntity(EntitySpec<? extends Entity> wrapperApplication) {
        if (!canUnwrapEntity(wrapperApplication)) {
            return wrapperApplication;
        }
        EntitySpec<?> wrappedEntity = Iterables.getOnlyElement(wrapperApplication.getChildren());
        @SuppressWarnings("unchecked")
        EntitySpec<? extends Application> wrapperApplicationTyped = (EntitySpec<? extends Application>) wrapperApplication;
        EntityManagementUtils.mergeWrapperParentSpecToChildEntity(wrapperApplicationTyped, wrappedEntity);
        return wrappedEntity;
    }
    
    /** Unwraps a wrapped {@link Application} if appropriate.
     * This is like {@link #canUnwrapEntity(EntitySpec)} with an additional check that the wrapped child is an {@link Application}. 
     * See {@link #WRAPPER_APP_MARKER} for an overview. 
     * Also see {@link #canUnwrapApplication(EntitySpec)} to test whether it will unwrap. */
    public static EntitySpec<? extends Application> unwrapApplication(EntitySpec<? extends Application> wrapperApplication) {
        if (!canUnwrapApplication(wrapperApplication)) {
            return wrapperApplication;
        }
        @SuppressWarnings("unchecked")
        EntitySpec<? extends Application> wrappedApplication = (EntitySpec<? extends Application>) unwrapEntity(wrapperApplication);
        return wrappedApplication;
    }
    
    /** Modifies the child so it includes the inessential setup of its parent,
     * for use when unwrapping specific children, but a name or other item may have been set on the parent.
     * See {@link #WRAPPER_APP_MARKER}. */
    private static void mergeWrapperParentSpecToChildEntity(EntitySpec<? extends Application> wrapperParent, EntitySpec<?> wrappedChild) {
        if (Strings.isNonEmpty(wrapperParent.getDisplayName())) {
            wrappedChild.displayName(wrapperParent.getDisplayName());
        }
        
        wrappedChild.locations(wrapperParent.getLocations());
        
        if (!wrapperParent.getParameters().isEmpty())
            wrappedChild.parametersReplace(wrapperParent.getParameters());

        // prefer the wrapper ID (change in 2016-01); see notes on the catalogItemIdIfNotNull method
        wrappedChild.catalogItemIdIfNotNull(wrapperParent.getCatalogItemId());

        // NB: this clobber's child config wherever they conflict; might prefer to deeply merge maps etc
        // (or maybe even prevent the merge in these cases; 
        // not sure there is a compelling reason to have config on a pure-wrapper parent)
        Map<ConfigKey<?>, Object> configWithoutWrapperMarker = Maps.filterKeys(wrapperParent.getConfig(), Predicates.not(Predicates.<ConfigKey<?>>equalTo(EntityManagementUtils.WRAPPER_APP_MARKER)));
        wrappedChild.configure(configWithoutWrapperMarker);
        wrappedChild.configure(wrapperParent.getFlags());
        
        // copying tags to all entities may be something the caller wants to control,
        // e.g. if we're adding multiple, the caller might not want to copy the parent
        // (the BrooklynTags.YAML_SPEC tag will include the parents source including siblings),
        // but OTOH they might because otherwise the parent's tags might get lost.
        // also if we are unwrapping multiple registry references we will get the YAML_SPEC for each;
        // putting the parent's tags first however causes the preferred (outer) one to be retrieved first.
        wrappedChild.tagsReplace(MutableList.copyOf(wrapperParent.getTags()).appendAll(wrappedChild.getTags()));
    }

    public static EntitySpec<? extends Application> newWrapperApp() {
        return EntitySpec.create(BasicApplication.class).configure(WRAPPER_APP_MARKER, true);
    }
    
    /** As {@link #canUnwrapEntity(EntitySpec)}
     * but additionally requiring that the wrapped item is an {@link Application},
     * for use when the context requires an {@link Application} ie a root of a spec.
     * @see #WRAPPER_APP_MARKER */
    public static boolean canUnwrapApplication(EntitySpec<? extends Application> wrapperApplication) {
        if (!canUnwrapEntity(wrapperApplication)) return false;

        EntitySpec<?> childSpec = Iterables.getOnlyElement(wrapperApplication.getChildren());
        return (childSpec.getType()!=null && Application.class.isAssignableFrom(childSpec.getType()));
    }
    /** @deprecated since 0.9.0 use {@link #canUnwrapApplication(EntitySpec)} */ @Deprecated
    public static boolean canPromoteWrappedApplication(EntitySpec<? extends Application> app) {
        return canUnwrapApplication(app);
    }
    
    /** Returns true if the spec is for a wrapper app with no important settings, wrapping a single child entity. 
     * for use when adding from a plan specifying multiple entities but there is nothing significant at the application level,
     * and the context would like to flatten it to remove the wrapper yielding just a single entity.
     * (but note the result is not necessarily an {@link Application}; 
     * see {@link #canUnwrapApplication(EntitySpec)} if that is required).
     * <p>
     * Note callers will normally use one of {@link #unwrapEntity(EntitySpec)} or {@link #unwrapApplication(EntitySpec)}.
     * 
     * @see #WRAPPER_APP_MARKER for an overview */
    public static boolean canUnwrapEntity(EntitySpec<? extends Entity> spec) {
        return isWrapperApp(spec) && hasSingleChild(spec) &&
            // these "brooklyn.*" items on the app rather than the child absolutely prevent unwrapping
            // as their semantics could well be different whether they are on the parent or the child
            spec.getEnrichers().isEmpty() &&
            spec.getEnricherSpecs().isEmpty() &&
            spec.getInitializers().isEmpty() &&
            spec.getPolicies().isEmpty() &&
            spec.getPolicySpecs().isEmpty() &&
            // these items prevent merge only if they are defined at both levels
            (spec.getLocations().isEmpty() || Iterables.getOnlyElement(spec.getChildren()).getLocations().isEmpty())
            // TODO what should we do with parameters? currently clobbers due to EntitySpec.parameters(...) behaviour.
//            && (spec.getParameters().isEmpty() || Iterables.getOnlyElement(spec.getChildren()).getParameters().isEmpty())
            ;
    }
    /** @deprecated since 0.9.0 use {@link #canUnwrapEntity(EntitySpec)} */ @Deprecated
    public static boolean canPromoteChildrenInWrappedApplication(EntitySpec<? extends Application> spec) {
        return canUnwrapEntity(spec);
    }

    public static boolean isWrapperApp(EntitySpec<?> spec) {
        return Boolean.TRUE.equals(spec.getConfig().get(EntityManagementUtils.WRAPPER_APP_MARKER));
    }

    private static boolean hasSingleChild(EntitySpec<?> spec) {
        return spec.getChildren().size() == 1;
    }

}
