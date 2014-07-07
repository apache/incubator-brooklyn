package brooklyn.osgi.tests;


import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(SimpleEntityImpl.class)
public interface SimpleEntity extends Entity {

}
