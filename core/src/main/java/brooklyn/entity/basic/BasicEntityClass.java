package brooklyn.entity.basic;

import groovy.xml.Entity;

import java.util.Collection;

import brooklyn.entity.Effector;
import brooklyn.entity.EntityClass;
import brooklyn.event.Sensor;

public class BasicEntityClass implements EntityClass {

	private static final long serialVersionUID = -5097081646153721468L;
	
	private Class<? extends Entity> javaClass;

	public BasicEntityClass(Class<? extends Entity> javaClass) {
		this.javaClass = javaClass;
	}
	
	@Override
	public String getName() {
		return javaClass.getCanonicalName();
	}

	//TODO find the values below by introspecting on the class (could cache to a transient?)
	
	@Override
	public Collection<Sensor<?>> getSensors() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Effector<?, ?>> getEffectors() {
		// TODO Auto-generated method stub
		return null;
	}

}
