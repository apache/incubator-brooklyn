package org.overpaas.core.decorators;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

public interface OverpaasEntity extends Serializable {
	String getDisplayName();
	String getId();
	
	OverpaasApplication getApplication();
	
	/** ad hoc map for storing, e.g. description, icons, etc */
	Map getPresentationAttributes();

	/**
	 * Mutable properties on this entity.
	 * 
	 * Allows one to put arbitrary properties on entities which makes life much easier/dynamic, 
	 * though we lose something in type safety.
	 * 
	 * @return
	 */
	// e.g. jmxHost / jmxPort are handled as properties.
	Map<String,Object> getProperties();
	
	Collection<? extends GroupEntity> getParents();
	void addParent(GroupEntity e);
	
	Collection<? extends Field> getSensors();
	Collection<? extends Method> getEffectors();
}
