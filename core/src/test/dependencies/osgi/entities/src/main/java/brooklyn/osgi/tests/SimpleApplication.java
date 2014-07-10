package brooklyn.osgi.tests;


import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(SimpleApplicationImpl.class)
public interface SimpleApplication extends StartableApplication {

}
