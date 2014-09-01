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
package io.brooklyn.camp.brooklyn.spi.dsl.methods;

import io.brooklyn.camp.brooklyn.BrooklynCampConstants;
import io.brooklyn.camp.brooklyn.spi.dsl.BrooklynDslDeferredSupplier;

import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.event.basic.Sensors;
import brooklyn.management.Task;
import brooklyn.management.internal.EntityManagerInternal;
import brooklyn.util.guava.Maybe;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.Tasks;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class DslComponent extends BrooklynDslDeferredSupplier<Entity> {

    private static final long serialVersionUID = -7715984495268724954L;
    
    private final String componentId;
	private final DslComponent scopeComponent;
	private final Scope scope;

	public DslComponent(String componentId) {
		this(Scope.GLOBAL, componentId);
	}
	
	public DslComponent(Scope scope, String componentId) {
	    this(null, scope, componentId);
	}
	
	public DslComponent(DslComponent scopeComponent, Scope scope, String componentId) {
	    Preconditions.checkNotNull(scope, "scope");
	    this.scopeComponent = scopeComponent;
	    this.componentId = componentId;
	    this.scope = scope;
	}

	// ---------------------------
	
    @Override
    public Task<Entity> newTask() {
        return TaskBuilder.<Entity>builder().name("component("+componentId+")").body(
            new EntityInScopeFinder(scopeComponent, scope, componentId)).build();
    }
    
    protected static class EntityInScopeFinder implements Callable<Entity> {
        protected final DslComponent scopeComponent;
        protected final Scope scope;
        protected final String componentId;

        public EntityInScopeFinder(DslComponent scopeComponent, Scope scope, String componentId) {
            this.scopeComponent = scopeComponent;
            this.scope = scope;
            this.componentId = componentId;
        }

        protected EntityInternal getEntity() {
            if (scopeComponent!=null) {
                return (EntityInternal)scopeComponent.get();
            } else {
                return entity();
            }
        }
        
        @Override
        public Entity call() throws Exception {
            Iterable<Entity> entitiesToSearch = null;
            switch (scope) {
                case THIS:
                    return getEntity();
                case PARENT:
                    return getEntity().getParent();
                case GLOBAL:
                    entitiesToSearch = ((EntityManagerInternal)getEntity().getManagementContext().getEntityManager())
                        .getAllEntitiesInApplication( entity().getApplication() );
                    break;
                case DESCENDANT:
                    entitiesToSearch = Entities.descendants(getEntity());
                    break;
                case ANCESTOR:
                    entitiesToSearch = Entities.ancestors(getEntity());
                    break;
                case SIBLING:
                    entitiesToSearch = getEntity().getParent().getChildren();
                    break;
                case CHILD:
                    entitiesToSearch = getEntity().getChildren();
                    break;
                default:
                    throw new IllegalStateException("Unexpected scope "+scope);
            }
            
            Optional<Entity> result = Iterables.tryFind(entitiesToSearch, EntityPredicates.configEqualTo(BrooklynCampConstants.PLAN_ID, componentId));
            
            if (result.isPresent())
                return result.get();
            
            // TODO may want to block and repeat on new entities joining?
            throw new NoSuchElementException("No entity matching id " + componentId+
                (scope==Scope.GLOBAL ? "" : ", in scope "+scope+" wrt "+getEntity()+
                (scopeComponent!=null ? " ("+scopeComponent+" from "+entity()+")" : "")));
        }        
    }
    
    // -------------------------------

    // DSL words which move to a new component
    
    public DslComponent entity(String scopeOrId) {
        return new DslComponent(this, Scope.GLOBAL, scopeOrId);
    }
    public DslComponent child(String scopeOrId) {
        return new DslComponent(this, Scope.CHILD, scopeOrId);
    }
    public DslComponent sibling(String scopeOrId) {
        return new DslComponent(this, Scope.SIBLING, scopeOrId);
    }
    public DslComponent descendant(String scopeOrId) {
        return new DslComponent(this, Scope.DESCENDANT, scopeOrId);
    }
    public DslComponent ancestor(String scopeOrId) {
        return new DslComponent(this, Scope.ANCESTOR, scopeOrId);
    }
    
    @Deprecated /** @deprecated since 0.7.0 */
    public DslComponent component(String scopeOrId) {
        return new DslComponent(this, Scope.GLOBAL, scopeOrId);
    }
    
    public DslComponent parent() {
        return new DslComponent(this, Scope.PARENT, "");
    }
    
    public DslComponent component(String scope, String id) {
        if (!DslComponent.Scope.isValid(scope)) {
            throw new IllegalArgumentException(scope + " is not a vlaid scope");
        }
        return new DslComponent(this, DslComponent.Scope.fromString(scope), id);
    }

    // DSL words which return things
    
	public BrooklynDslDeferredSupplier<?> attributeWhenReady(final String sensorName) {
		return new AttributeWhenReady(this, sensorName);
	}
	// class simply makes the memento XML files nicer
	protected static class AttributeWhenReady extends BrooklynDslDeferredSupplier<Object> {
        private static final long serialVersionUID = 1740899524088902383L;
        private final DslComponent component;
        private final String sensorName;
        public AttributeWhenReady(DslComponent component, String sensorName) {
            this.component = Preconditions.checkNotNull(component);
            this.sensorName = sensorName;
        }
        @SuppressWarnings("unchecked")
        @Override
        public Task<Object> newTask() {
            Entity targetEntity = component.get();
            Sensor<?> targetSensor = targetEntity.getEntityType().getSensor(sensorName);
            if (!(targetSensor instanceof AttributeSensor<?>)) {
                targetSensor = Sensors.newSensor(Object.class, sensorName);
            }
            return (Task<Object>) DependentConfiguration.attributeWhenReady(targetEntity, (AttributeSensor<?>)targetSensor);
        }
        @Override
        public String toString() {
            return component.toString()+"."+"attributeWhenReady("+sensorName+")";
        }
	}

	public BrooklynDslDeferredSupplier<?> config(final String keyName) {
        return new DslConfigSupplier(this, keyName);
    }
    protected final static class DslConfigSupplier extends BrooklynDslDeferredSupplier<Object> {
        private final DslComponent component;
        private final String keyName;
        private static final long serialVersionUID = -4735177561947722511L;

        public DslConfigSupplier(DslComponent component, String keyName) {
            this.component = Preconditions.checkNotNull(component);
            this.keyName = keyName;
        }

        @Override
        public Task<Object> newTask() {
            return Tasks.builder().name("retrieving config for "+keyName).dynamic(false).body(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    Entity targetEntity = component.get();
                    return targetEntity.getConfig(ConfigKeys.newConfigKey(Object.class, keyName));
                }
            }).build();
        }

        @Override
        public String toString() {
            return component.toString()+"."+"config("+keyName+")";
        }
    }

	public static enum Scope {
	    GLOBAL ("global"),
	    CHILD ("child"),
	    PARENT ("parent"),
	    SIBLING ("sibling"),
	    DESCENDANT ("descendant"),
	    ANCESTOR("ancestor"),
	    THIS ("this");
	    
	    public static final Set<Scope> VALUES = ImmutableSet.of(GLOBAL, CHILD, PARENT, SIBLING, DESCENDANT, ANCESTOR, THIS);
	    
	    private final String name;
	    
	    private Scope(String name) {
	        this.name = name;
	    }
	    
	    public static Scope fromString(String name) {
	        return tryFromString(name).get();
	    }
	    
	    public static Maybe<Scope> tryFromString(String name) {
	        for (Scope scope : VALUES)
	            if (scope.name.toLowerCase().equals(name.toLowerCase()))
	                return Maybe.of(scope);
	        return Maybe.absent(new IllegalArgumentException(name + " is not a valid scope"));
	    }
	    
	    public static boolean isValid(String name) {
	        for (Scope scope : VALUES)
	            if (scope.name.toLowerCase().equals(name.toLowerCase()))
	                return true;
	        return false;
	    }
	}


    @Override
    public String toString() {
        return "$brooklyn:component("+
            (scopeComponent==null ? "" : scopeComponent+", ")+
            (scope==Scope.GLOBAL ? "" : scope+", ")+
            componentId+
            ")";
    }

}