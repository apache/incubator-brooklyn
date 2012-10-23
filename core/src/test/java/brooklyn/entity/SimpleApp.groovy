package brooklyn.entity;

import groovy.transform.InheritConstructors;
import brooklyn.entity.basic.AbstractApplication;

@InheritConstructors
public class SimpleApp extends AbstractApplication {
    
    public SimpleEntity newSimpleChild() {
        SimpleEntity child = new SimpleEntity(this);
        if (getManagementSupport().isDeployed())
            getManagementSupport().getManagementContext(false).manage(child);
        return child;
    }
    
}
