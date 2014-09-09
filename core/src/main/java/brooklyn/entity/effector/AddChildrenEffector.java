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
package brooklyn.entity.effector;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFunctions;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.effector.Effectors.EffectorBuilder;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.management.classloading.JavaBrooklynClassLoadingContext;
import brooklyn.util.collections.MutableList;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gson.Gson;

/** Entity initializer which defines an effector which adds a child blueprint to an entity.
 * <p>
 * One of the config keys {@link #BLUEPRINT_YAML} (containing a YAML blueprint (map or string)) 
 * or {@link #BLUEPRINT_TYPE} (containing a string referring to a catalog type) should be supplied, but not both.
 * Parameters defined here are supplied as config during the entity creation.
 * 
 * @since 0.7.0 */
@Beta
public class AddChildrenEffector extends AddEffector {
    
    private static final Logger log = LoggerFactory.getLogger(AddChildrenEffector.class);
    
    public static final ConfigKey<Object> BLUEPRINT_YAML = ConfigKeys.newConfigKey(Object.class, "blueprint_yaml");
    public static final ConfigKey<String> BLUEPRINT_TYPE = ConfigKeys.newStringConfigKey("blueprint_type");
    public static final ConfigKey<Boolean> AUTO_START = ConfigKeys.newBooleanConfigKey("auto_start");
    
    public AddChildrenEffector(ConfigBag params) {
        super(newEffectorBuilder(params).build());
    }
    
    public AddChildrenEffector(Map<String,String> params) {
        this(ConfigBag.newInstance(params));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static EffectorBuilder<List<String>> newEffectorBuilder(ConfigBag params) {
        EffectorBuilder<List<String>> eff = (EffectorBuilder) AddEffector.newEffectorBuilder(List.class, params);
        eff.impl(new Body(eff.buildAbstract(), params));
        return eff;
    }

    protected static class Body extends EffectorBody<List<String>> {

        private final Effector<?> effector;
        private final String blueprintBase;
        private final Boolean autostart;

        public Body(Effector<?> eff, ConfigBag params) {
            this.effector = eff;
            String newBlueprint = null;
            Object yaml = params.get(BLUEPRINT_YAML);
            if (yaml instanceof Map) {
                newBlueprint = new Gson().toJson(yaml);
            } else if (yaml instanceof String) {
                newBlueprint = (String) yaml;
            } else if (yaml!=null) {
                throw new IllegalArgumentException(this+" requires map or string in "+BLUEPRINT_YAML+"; not "+yaml.getClass()+" ("+yaml+")");
            }
            String blueprintType = params.get(BLUEPRINT_TYPE);
            if (blueprintType!=null) {
                if (newBlueprint!=null) {
                    throw new IllegalArgumentException(this+" cannot take both "+BLUEPRINT_TYPE+" and "+BLUEPRINT_YAML);
                }
                newBlueprint = "services: [ { type: "+blueprintType+" } ]";
            }
            if (newBlueprint==null) {
                throw new IllegalArgumentException(this+" requires either "+BLUEPRINT_TYPE+" or "+BLUEPRINT_YAML);
            }
            blueprintBase = newBlueprint;
            autostart = params.get(AUTO_START);
        }

        @Override
        public List<String> call(ConfigBag params) {
            params = getMergedParams(effector, params);
            
            String blueprint = blueprintBase;
            if (!params.isEmpty()) { 
                blueprint = blueprint+"\n"+"brooklyn.config: "+
                    new Gson().toJson(params.getAllConfig());
            }

            log.debug(this+" adding children to "+entity()+":\n"+blueprint);
            AddChildrenResult result = addChildren(entity(), blueprint, autostart, Duration.ZERO);
            log.debug(this+" added children to "+entity()+": "+result.getChildren());
            return result.getTask().getUnchecked();
        }
    }

    public static class AddChildrenResult {
        public final List<Entity> children;
        public final Task<List<String>> task;
        public AddChildrenResult(List<Entity> children, Task<List<String>> task) {
            super();
            this.children = children;
            this.task = task;
        }
        public List<Entity> getChildren() {
          return children;
      }
        public Task<List<String>> getTask() {
          return task;
      }
    }
    
    public static AddChildrenResult addChildren(final EntityLocal parent, String yaml, Boolean start, Duration timeout) {
        log.debug("Creating child of "+parent+" from yaml:\n{}", yaml);
        
        ManagementContext mgmt = parent.getApplication().getManagementContext();
        CampPlatform camp = BrooklynServerConfig.getCampPlatform(mgmt).get();
        
        Reader input = new StringReader(yaml);
        AssemblyTemplate at = camp.pdp().registerDeploymentPlan(input);

        AssemblyTemplateInstantiator instantiator;
        try {
            instantiator = at.getInstantiator().newInstance();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
        if (instantiator instanceof AssemblyTemplateSpecInstantiator) {
            BrooklynClassLoadingContext loader = JavaBrooklynClassLoadingContext.newDefault(mgmt);
            EntitySpec<?> specA = ((AssemblyTemplateSpecInstantiator) instantiator).createSpec(at, camp, loader, false);

            boolean promoted;

            // see whether we can promote children
            List<EntitySpec<?>> specs = MutableList.of();
            if (hasNoNameOrCustomKeysOrRoot(at, specA)) {
                // we can promote
                promoted = true;
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
                promoted = false;
                specs.add(specA);
            }

            final List<Entity> children = MutableList.of();
            for (EntitySpec<?> spec: specs) {
                Entity child = (Entity)parent.addChild(spec);
                Entities.manage(child);
                children.add(child);
            }

            String childrenCountString;
            if (promoted) {
                int size = children.size();
                childrenCountString = size+" "+(size!=1 ? "children" : "child"); 
            } else {
                int size = specA.getChildren().size();
                childrenCountString = "entity with "+size+" "+(size!=1 ? "children" : "child");
            }

            TaskBuilder<List<String>> taskM = Tasks.<List<String>>builder().name("add children")
                .dynamic(true)
                .tag(BrooklynTaskTags.NON_TRANSIENT_TASK_TAG)
                .body(new Callable<List<String>>() {
                    @Override public List<String> call() throws Exception {
                        return ImmutableList.copyOf(Iterables.transform(children, EntityFunctions.id()));
                    }})
                    .description("Add" + (start==null ? " and potentially start" : start ? " and start" : "") + " "+childrenCountString);
            TaskBuilder<?> taskS = Tasks.builder().parallel(true).name("add (parallel)")
                .description(
                    (start==null ? "Add or start" : start ? "Start" : "Add")+" each new entity");

            // should we autostart?
            for (Entity child: children) {
                if (Boolean.TRUE.equals(start) || (start==null && child instanceof Startable)) {
                    taskS.add(Effectors.invocation(child, Startable.START, ImmutableMap.of("locations", ImmutableList.of())));
                } else {
                    taskS.add(Tasks.builder().name("create").description("Created and added as child of "+parent)
                        .body(new Runnable() { public void run() {} })
                        .tag(BrooklynTaskTags.tagForTargetEntity(child))
                        .build());
                }
            }
            taskM.add(taskS.build());
            Task<List<String>> task = Entities.submit(parent, taskM.build());

            // wait a few ms in case start is trivially simple, save the client a call to get the task result
            task.blockUntilEnded(timeout);

            return new AddChildrenResult(children, task);
        } else {
            throw new IllegalStateException("Spec could not be parsed to supply a compatible instantiator");
        }
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
        if (!Strings.isEmpty(template.getName())) {
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
