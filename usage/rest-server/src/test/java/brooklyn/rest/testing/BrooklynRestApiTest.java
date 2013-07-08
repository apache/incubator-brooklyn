package brooklyn.rest.testing;

import brooklyn.rest.util.DefaultExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.location.LocationRegistry;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.rest.BrooklynRestApi;
import brooklyn.rest.BrooklynRestApiLauncherTest;
import brooklyn.rest.resources.AbstractBrooklynRestResource;
import brooklyn.rest.util.BrooklynRestResourceUtils;
import brooklyn.rest.util.NullHttpServletRequestProvider;
import brooklyn.rest.util.NullServletConfigProvider;

import com.yammer.dropwizard.testing.ResourceTest;

public abstract class BrooklynRestApiTest extends ResourceTest {

    private static final Logger log = LoggerFactory.getLogger(BrooklynRestApiTest.class);
    
    private ManagementContext manager;
    
    protected synchronized ManagementContext getManagementContext() {
        if (manager==null) {
            manager = new LocalManagementContext();
            BrooklynRestApiLauncherTest.forceUseOfDefaultCatalogWithJavaClassPath(manager);
            BasicLocationRegistry.setupLocationRegistryForTesting(manager);
        }
        return manager;
    }
    
    public LocationRegistry getLocationRegistry() {
        return new BrooklynRestResourceUtils(getManagementContext()).getLocationRegistry();
    }

    @Override
    protected final void addResource(Object resource) {
        // seems we have to provide our own injector because the jersey test framework 
        // doesn't inject ServletConfig and it all blows up
        addProvider(NullServletConfigProvider.class);
        addProvider(NullHttpServletRequestProvider.class);
        
      super.addResource(resource);
      if (resource instanceof AbstractBrooklynRestResource) {
          ((AbstractBrooklynRestResource)resource).injectManagementContext(getManagementContext());
      }
    }
    
    protected void addResources() {
        addProvider(DefaultExceptionMapper.class);
        for (Object r: BrooklynRestApi.getBrooklynRestResources())
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
