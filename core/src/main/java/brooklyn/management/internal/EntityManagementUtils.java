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

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.AbstractBrooklynObjectSpec;
import brooklyn.catalog.internal.CatalogItemDtoAbstract;
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
import brooklyn.plan.PlanNotRecognized;
import brooklyn.plan.PlanToSpecCreator;
import brooklyn.plan.SpecCreatorFactory;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

/** Utility methods for working with entities and applications */
public class EntityManagementUtils {
    private static final Logger log = LoggerFactory.getLogger(EntityManagementUtils.class);

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

    public static AbstractBrooklynObjectSpec<?, ?> createSpec(ManagementContext mgmt, String yaml, BrooklynClassLoadingContext loader) {
        return createSpec(mgmt, new StringReader(yaml), loader);
    }

    public static AbstractBrooklynObjectSpec<?, ?> createSpec(ManagementContext mgmt, Reader yaml, BrooklynClassLoadingContext loader) {
        Collection<String> types = new ArrayList<String>();
        for(PlanToSpecCreator c : SpecCreatorFactory.all()) {
            try {
                return c.createSpec(mgmt, yaml, loader);
            } catch (PlanNotRecognized e) {
                types.add(c.getName());
            }
        }
        throw new PlanNotRecognized("Invalid plan, tried parsing with " + types);
    }

    public static CatalogItemDtoAbstract<?, ?> createCatalogItem(ManagementContext mgmt, String yaml) {
        Collection<String> types = new ArrayList<String>();
        for(PlanToSpecCreator c : SpecCreatorFactory.all()) {
            try {
                return c.createCatalogItem(mgmt, new StringReader(yaml));
            } catch (PlanNotRecognized e) {
                types.add(c.getName());
            }
        }
        throw new PlanNotRecognized("Invalid plan, tried parsing with " + types);
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

    public static Task<Void> start(Application app) {
        return Entities.invokeEffector((EntityLocal)app, app, Startable.START,
            // locations already set in the entities themselves;
            // TODO make it so that this arg does not have to be supplied to START !
            MutableMap.of("locations", MutableList.of()));
    }
    
    public static CreationResult<List<Entity>, List<String>> addChildren(final EntityLocal parent, String yaml, Boolean start) {
        if (Boolean.FALSE.equals(start))
            return CreationResult.of(addChildrenUnstarted(parent, yaml), null);
        return addChildrenStarting(parent, yaml);
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

    private static JavaBrooklynClassLoadingContext createDefaultLoader(
            ManagementContext mgmt) {
        return JavaBrooklynClassLoadingContext.create(mgmt);
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

    public static boolean isApplicationSpec(EntitySpec<?> spec) {
        return !Boolean.TRUE.equals(spec.getConfig().get(EntityManagementUtils.WRAPPER_APP_MARKER));
    }

}
