package io.brooklyn.camp.brooklyn.spi.dsl.methods;

import io.brooklyn.camp.brooklyn.BrooklynCampConstants;
import io.brooklyn.camp.brooklyn.spi.dsl.BrooklynDslDeferredSupplier;

import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;

import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.event.basic.Sensors;
import brooklyn.management.Task;
import brooklyn.management.internal.EntityManagerInternal;
import brooklyn.util.task.TaskBuilder;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public class DslComponent extends BrooklynDslDeferredSupplier<Entity> {

	private final String componentId;
	private final Scope scope;

	public DslComponent(String componentId) {
		this(Scope.GLOBAL, componentId);
	}
	
	public DslComponent(Scope scope, String componentId) {
	    this.componentId = componentId;
	    this.scope = scope;
	}

    @Override
    public Task<Entity> newTask() {
        return TaskBuilder.<Entity>builder().name("component("+componentId+")").body(new Callable<Entity>() {
            @Override
            public Entity call() throws Exception {
                if (scope == Scope.THIS)
                    return entity();
                if (scope == Scope.PARENT)
                    return entity().getParent();
                Iterable<Entity> entitiesToSearch = null;
                if (scope == Scope.GLOBAL)
                    entitiesToSearch = ((EntityManagerInternal)entity().getManagementContext().getEntityManager())
                        .getAllEntitiesInApplication( entity().getApplication() );
                if (scope == Scope.DESCENDANT) {
                    entitiesToSearch = Sets.newHashSet();
                    addDescendants(entity(), (Set<Entity>)entitiesToSearch);
                }
                if (scope == Scope.SIBLING) {
                    entitiesToSearch = entity().getParent().getChildren();
                }
                if (scope == Scope.CHILD)
                    entitiesToSearch = entity().getChildren();
                Optional<Entity> result = Iterables.tryFind(entitiesToSearch, new Predicate<Entity>() {
                    @Override
                    public boolean apply(Entity input) {
                        return componentId.equals(input.getConfig(BrooklynCampConstants.PLAN_ID));
                    }
                });
                if (result.isPresent())
                    return result.get();
                
                // TODO may want to block and repeat on new entities joining?
                throw new NoSuchElementException("No entity matching id " + componentId);
            }
        }).build();
    }
    
    private void addDescendants(Entity entity, Set<Entity> entities) {
        entities.add(entity);
        for (Entity child : entity.getChildren()) {
            addDescendants(child, entities);
        }
    }
    
	public BrooklynDslDeferredSupplier<?> attributeWhenReady(final String sensorName) {
		return new BrooklynDslDeferredSupplier<Object>() {
			@SuppressWarnings("unchecked")
            @Override
			public Task<Object> newTask() {
				Entity targetEntity = DslComponent.this.get();
				Sensor<?> targetSensor = targetEntity.getEntityType().getSensor(sensorName);
				if (!(targetSensor instanceof AttributeSensor<?>)) {
					targetSensor = Sensors.newSensor(Object.class, sensorName);
				}
				return (Task<Object>) DependentConfiguration.attributeWhenReady(targetEntity, (AttributeSensor<?>)targetSensor);
			}
		};
	}
	
	public static class Scope {
	    public static final Scope GLOBAL = new Scope("global");
	    public static final Scope CHILD = new Scope("child");
	    public static final Scope PARENT = new Scope("parent");
	    public static final Scope SIBLING = new Scope("sibling");
	    public static final Scope DESCENDANT = new Scope("descendant");
	    public static final Scope THIS = new Scope("this");
	    
	    public static final Set<Scope> VALUES = ImmutableSet.of(GLOBAL, CHILD, PARENT, SIBLING, DESCENDANT, THIS);
	    
	    private final String name;
	    
	    private Scope(String name) {
	        this.name = name;
	    }
	    
	    public static Scope fromString(String name) {
	        for (Scope scope : VALUES)
	            if (scope.name.equals(name))
	                return scope;
	        throw new IllegalArgumentException(name + " is not a valid scope");
	    }
	    
	    public static boolean isValid(String name) {
	        for (Scope scope : VALUES)
	            if (scope.name.equals(name))
	                return true;
	        return false;
	    }
	}

}