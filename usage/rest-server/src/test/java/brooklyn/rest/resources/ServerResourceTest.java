package brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.BrooklynVersion;
import brooklyn.management.ManagementContext;
import brooklyn.rest.domain.HighAvailabilitySummary;
import brooklyn.rest.testing.BrooklynRestResourceTest;
import brooklyn.test.Asserts;

import com.google.common.collect.ImmutableSet;

@Test(singleThreaded = true)
public class ServerResourceTest extends BrooklynRestResourceTest {

    private static final Logger log = LoggerFactory.getLogger(ServerResourceTest.class);

    @Override
    protected void setUpResources() throws Exception {
        addResources();
    }

    @Test
    public void testGetVersion() throws Exception {
        String version = client().resource("/v1/server/version").get(String.class);
        assertEquals(version, BrooklynVersion.get());
    }

    @Test
    public void testGetStatus() throws Exception {
        String status = client().resource("/v1/server/status").get(String.class);
        assertEquals(status, "MASTER");
    }

    @Test
    public void testGetHighAvailability() throws Exception {
        // Note by default management context from super is started without HA enabled.
        // Therefore can only assert a minimal amount of stuff.
        HighAvailabilitySummary summary = client().resource("/v1/server/highAvailability").get(HighAvailabilitySummary.class);
        
        String ownNodeId = getManagementContext().getManagementNodeId();
        assertEquals(summary.getOwnId(), ownNodeId);
        assertEquals(summary.getMasterId(), ownNodeId);
        assertEquals(summary.getNodes().keySet(), ImmutableSet.of(ownNodeId));
        assertEquals(summary.getNodes().get(ownNodeId).getNodeId(), ownNodeId);
        assertEquals(summary.getNodes().get(ownNodeId).getStatus(), "MASTER");
        assertNotNull(summary.getNodes().get(ownNodeId).getTimestampUtc());
    }

    @Test
    public void testReloadsBrooklynProperties() throws Exception {
        final AtomicInteger reloadCount = new AtomicInteger();
        getManagementContext().addPropertiesReloadListener(new ManagementContext.PropertiesReloadListener() {
            @Override public void reloaded() {
                reloadCount.incrementAndGet();
            }});
        client().resource("/v1/server/properties/reload").post();
        assertEquals(reloadCount.get(), 1);
    }
    
    // TODO Do not run this! It does a system.exit in ServerResource.shutdown
    @Test(enabled=false)
    public void testShutdown() throws Exception {
        assertTrue(getManagementContext().isRunning());
        
        client().resource("/v1/server/shutdown").post();
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertFalse(getManagementContext().isRunning());
            }});
    }
}
