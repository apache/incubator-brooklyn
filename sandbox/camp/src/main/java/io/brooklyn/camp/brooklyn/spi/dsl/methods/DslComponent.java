package io.brooklyn.camp.brooklyn.spi.dsl.methods;

import io.brooklyn.camp.brooklyn.spi.dsl.BrooklynDslDeferredSupplier;

import java.util.concurrent.Callable;

import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.event.basic.Sensors;
import brooklyn.management.Task;
import brooklyn.util.task.TaskBuilder;

import com.google.common.base.Preconditions;

public class DslComponent extends BrooklynDslDeferredSupplier<Entity> {

	private final String componentId;

	public DslComponent(String componentId) {
		this.componentId = componentId;
	}

    public Task<Entity> newTask() {
        return TaskBuilder.<Entity>builder().name("component("+componentId+")").body(new Callable<Entity>() {
            @Override
            public Entity call() throws Exception {
                return Preconditions.checkNotNull(entity().getApplication().getManagementContext().getEntityManager().getEntity(componentId), "No entity matching id %s", componentId);
            }
        }).build();
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

}