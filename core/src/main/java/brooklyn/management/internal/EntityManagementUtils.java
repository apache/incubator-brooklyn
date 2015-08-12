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

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;

import java.io.StringReader;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.Task;
import org.apache.brooklyn.management.classloading.BrooklynClassLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
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
import brooklyn.management.classloading.JavaBrooklynClassLoadingContext;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
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

    /** convenience for accessing camp */
    public static Maybe<CampPlatform> getCampPlatform(ManagementContext mgmt) {
        return BrooklynServerConfig.getCampPlatform(mgmt);
    }
    
    /** as {@link #createApplication(ManagementContext, EntitySpec)} but for a YAML spec */
    public static <T extends Application> T createUnstarted(ManagementContext mgmt, String yaml) {
        AssemblyTemplate at = getCampPlatform(mgmt).get().pdp().registerDeploymentPlan( new StringReader(yaml) );
        return createUnstarted(mgmt, at);
    }
    
    /** as {@link #createApplication(ManagementContext, EntitySpec)} but for an assembly template */
    @SuppressWarnings("unchecked")
    public static <T extends Application> T createUnstarted(ManagementContext mgmt, AssemblyTemplate at) {
        CampPlatform camp = getCampPlatform(mgmt).get();
        AssemblyTemplateInstantiator instantiator;
        try {
            instantiator = at.getInstantiator().newInstance();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
        Assembly assembly;
        if (instantiator instanceof AssemblyTemplateSpecInstantiator) {
            BrooklynClassLoadingContext loader = JavaBrooklynClassLoadingContext.create(mgmt);
            
            EntitySpec<?> spec = ((AssemblyTemplateSpecInstantiator) instantiator).createSpec(at, camp, loader, true);
            Entity app = mgmt.getEntityManager().createEntity(spec);
            Entities.startManagement((Application)app, mgmt);
            return (T) app;
        } else {
            // currently, all brooklyn plans should produce the above; currently this will always throw Unsupported  
            try {
                assembly = instantiator.instantiate(at, camp);
                return (T) mgmt.getEntityManager().getEntity(assembly.getId());
            } catch (UnsupportedOperationException e) {
                if (at.getPlatformComponentTemplates()==null || at.getPlatformComponentTemplates().isEmpty()) {
                    if (at.getCustomAttributes().containsKey("brooklyn.catalog"))
                        throw new IllegalArgumentException("Unrecognized application blueprint format: expected an application, not a brooklyn.catalog");
                    throw new IllegalArgumentException("Unrecognized application blueprint format: no services defined");
                }
                // map this (expected) error to a nicer message
                throw new IllegalArgumentException("Unrecognized application blueprint format");
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                throw new IllegalArgumentException("Invalid plan: "+at, e);
            }
        }
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

    public static CreationResult<? extends Application,Void> createStarting(ManagementContext mgmt, AssemblyTemplate at) {
        return start(createUnstarted(mgmt, at));
    }

    public static <T extends Application> CreationResult<T,Void> start(T app) {
        Task<Void> task = Entities.invokeEffector((EntityLocal)app, app, Startable.START,
            // locations already set in the entities themselves;
            // TODO make it so that this arg does not have to be supplied to START !
            MutableMap.of("locations", MutableList.of()));
        return CreationResult.of(app, task);
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
        CampPlatform camp = BrooklynServerConfig.getCampPlatform(mgmt).get();
        
        AssemblyTemplate at = camp.pdp().registerDeploymentPlan( new StringReader(yaml) );

        AssemblyTemplateInstantiator instantiator;
        try {
            instantiator = at.getInstantiator().newInstance();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
        if (instantiator instanceof AssemblyTemplateSpecInstantiator) {
            BrooklynClassLoadingContext loader = JavaBrooklynClassLoadingContext.create(mgmt);
            EntitySpec<?> specA = ((AssemblyTemplateSpecInstantiator) instantiator).createSpec(at, camp, loader, false);

            // see whether we can promote children
            List<EntitySpec<?>> specs = MutableList.of();
            if (hasNoNameOrCustomKeysOrRoot(at, specA)) {
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
        } else {
            throw new IllegalStateException("Spec could not be parsed to supply a compatible instantiator");
        }
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

    /** worker method to help determine whether child/children can be promoted */
    @Beta //where should this live long-term?
    public static boolean hasNoNameOrCustomKeysOrRoot(AssemblyTemplate template, EntitySpec<?> spec) {
        if (Strings.isNonEmpty(template.getName())) {
            if (spec.getChildren().size()==1) {
                String childName = Iterables.getOnlyElement(spec.getChildren()).getDisplayName();
                if (Strings.isEmpty(childName) || childName.equals(template.getName())) {
                    // if child has no name, or it's the same, could still promote
                } else {
                    return false;
                }
            } else {
                // if name set at root and promoting children would be ambiguous, do not promote 
                return false;
            }
        } else if (spec.getChildren().size()>1) {
            // don't allow multiple children if a name is specified as a root
            return false;
        }
        
        Set<String> rootAttrs = template.getCustomAttributes().keySet();
        for (String rootAttr: rootAttrs) {
            if (rootAttr.equals("brooklyn.catalog") || rootAttr.equals("brooklyn.config")) {
                // these do not block promotion
                continue;
            }
            if (rootAttr.startsWith("brooklyn.")) {
                // any others in 'brooklyn' namespace will block promotion
                return false;
            }
            // location is allowed in both, and is copied on promotion
            // (name also copied)
            // others are root currently are ignored on promotion; they are usually metadata
            // TODO might be nice to know what we are excluding
        }
        
        return true;
    }
    
}
