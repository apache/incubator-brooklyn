package org.overpaas.core.decorators;

import java.util.Collection;

public interface OverpaasApplication extends OverpaasEntity, Startable {

	public void registerEntity(OverpaasEntity entity);
	Collection<OverpaasEntity> getEntities();
	
}
