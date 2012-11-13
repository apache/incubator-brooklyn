package brooklyn.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.rest.core.BrooklynRestResourceUtils;
import brooklyn.rest.core.LocationStore;
import brooklyn.rest.resources.ApplicationResource;
import brooklyn.rest.resources.BrooklynResourceBase;
import brooklyn.rest.resources.EffectorResource;
import brooklyn.rest.resources.EntityResource;
import brooklyn.rest.resources.PolicyResource;
import brooklyn.rest.resources.SensorResource;

import com.yammer.dropwizard.testing.ResourceTest;

public abstract class BrooklynMgrResourceTest extends ResourceTest {

    private static final Logger log = LoggerFactory.getLogger(BrooklynMgrResourceTest.class);
    
    private ManagementContext manager;
    
    protected synchronized ManagementContext getManagementContext() {
        if (manager==null) {
            manager = new LocalManagementContext();
            BrooklynRestResourceUtils.changeLocationStore(LocationStore.withLocalhost());
        }
        return manager;
    }

    @Override
    protected final void addResource(Object resource) {
      super.addResource(resource);
      if (resource instanceof BrooklynResourceBase) {
          ((BrooklynResourceBase)resource).injectManagementContext(getManagementContext());
      }
    }
    
    protected void addResources() {
        for (Object r: BrooklynService.getBrooklynRestResources())
            addResource(r);
    }

    protected void stopManager() {
        for (Application app: getManagementContext().getApplications()) {
            try {
                ((AbstractApplication)app).stop();
            } catch (Exception e) {
                log.debug("Error stopping app "+app+" during test teardown: "+e);
            }
        }
    }
}
