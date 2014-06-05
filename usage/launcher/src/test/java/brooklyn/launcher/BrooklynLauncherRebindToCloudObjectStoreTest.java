package brooklyn.launcher;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.persister.jclouds.JcloudsBlobStoreBasedObjectStore;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.text.Identifiers;

@Test(groups="Integration")
// Jun 2014: Alex has confirmed setting Integration here means that inherited methods are NOT run as part of normal build
public class BrooklynLauncherRebindToCloudObjectStoreTest extends BrooklynLauncherRebindTestFixture {

    public static final String LOCATION_SPEC = "named:softlayer-objectstore-amsterdam-1";
    
    { persistenceLocationSpec = LOCATION_SPEC; }
    
    @Override
    protected BrooklynLauncher newLauncherBase() {
        return super.newLauncherBase().persistenceLocation(persistenceLocationSpec);
    }
    
    protected LocalManagementContextForTests newManagementContextForTests(BrooklynProperties props) {
        BrooklynProperties p2 = BrooklynProperties.Factory.newDefault();
        if (props!=null) p2.putAll(props);
        return new LocalManagementContextForTests(p2);
    }

    @Override
    protected String newTempPersistenceContainerName() {
        return "test-"+JavaClassNames.callerStackElement(0).getClassName()+"-"+Identifiers.makeRandomId(4);
    }
    
    protected String badContainerName() {
        return "container-does-not-exist-"+Identifiers.makeRandomId(4);
    }
    
    protected void checkPersistenceContainerNameIs(String expected) {
        assertEquals(getPersistenceContainerName(lastMgmt()), expected);
    }

    static String getPersistenceContainerName(ManagementContext managementContext) {
        BrooklynMementoPersisterToObjectStore persister = (BrooklynMementoPersisterToObjectStore)managementContext.getRebindManager().getPersister();
        JcloudsBlobStoreBasedObjectStore store = (JcloudsBlobStoreBasedObjectStore)persister.getObjectStore();
        return store.getContainerName();
    }

    protected void checkPersistenceContainerNameIsDefault() {
        checkPersistenceContainerNameIs(BrooklynServerConfig.PERSISTENCE_PATH_SEGMENT);
    }
    
}
