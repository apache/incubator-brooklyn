package org.overpaas.core.types.common

import java.util.Collection
import java.util.concurrent.ConcurrentHashMap

import org.overpaas.core.decorators.OverpaasApplication
import org.overpaas.core.decorators.OverpaasEntity
import org.overpaas.util.SerializableObservables.SerializableObservableMap

public abstract class AbstractOverpaasApplication extends AbstractGroupEntity implements OverpaasApplication {

	public AbstractOverpaasApplication(Map properties=[:]) {
		super(properties, null)
	}
	
	// --------------- application records all entities in use ----------------------
	final ObservableMap entities = new SerializableObservableMap(new ConcurrentHashMap<String,OverpaasEntity>());
	public void registerEntity(OverpaasEntity entity) {
		entities.put entity.id, entity
	}
	
	Collection<OverpaasEntity> getEntities()
	{ entities }

	protected void initApplicationRegistrant() { /* do nothing; we register ourself later */ }
	// record ourself as an entity in the entity list
	{ registerWithApplication this }
	
	// ---------------- lifecycle ---------------------
	
	/** default start will start all Startable children */
	public void start(Map addlProperties=[:]) {
		EntityStartUtils.startGroup addlProperties, this
	}
	
}
