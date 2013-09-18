package brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import brooklyn.rest.domain.AccessSummary;
import brooklyn.rest.testing.BrooklynRestResourceTest;

import com.sun.jersey.api.client.ClientResponse;

@Test(singleThreaded = true)
public class AccessResourceTest extends BrooklynRestResourceTest {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(AccessResourceTest.class);

    @Override
    protected void setUpResources() throws Exception {
        addResources();
    }

    @BeforeClass(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @AfterClass
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testGetAndSetAccessControl() throws Exception {
        // Default is everything allowed
        AccessSummary summary = client().resource("/v1/access").get(AccessSummary.class);
        assertTrue(summary.isLocationProvisioningAllowed());

        // Forbid location provisioning
        ClientResponse response = client().resource(
                "/v1/access/locationProvisioningAllowed")
                .queryParam("allowed", "false")
                .post(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());

        AccessSummary summary2 = client().resource("/v1/access").get(AccessSummary.class);
        assertFalse(summary2.isLocationProvisioningAllowed());
        
        // Allow location provisioning
        ClientResponse response2 = client().resource(
                "/v1/access/locationProvisioningAllowed")
                .queryParam("allowed", "true")
                .post(ClientResponse.class);
        assertEquals(response2.getStatus(), Response.Status.OK.getStatusCode());

        AccessSummary summary3 = client().resource("/v1/access").get(AccessSummary.class);
        assertTrue(summary3.isLocationProvisioningAllowed());
    }
}
