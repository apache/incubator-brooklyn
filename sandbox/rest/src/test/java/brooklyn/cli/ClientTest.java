package brooklyn.cli;

import brooklyn.rest.BrooklynService;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ClientTest {

    BrooklynService brooklynServer;
    Client brooklynClient;

    @BeforeClass
    public void testSetUp() throws Exception {
        // Start the REST server
        brooklynServer = BrooklynService.newBrooklynService();
        String[] args = {"server","config.sample.yml"};
        brooklynServer.runAsync(args);
    }

    @AfterClass
    public void testTearDown() {
        // Kill the REST server
        brooklynServer = null;
    }

    @Test(enabled = true)
    public void testCatalogEntitiesCommand() {
        System.out.println("testCatalogEntitiesCommand");
        String[] args = {"catalog-entities"};
        Client.main(args);
        //TODO: check that client output matches what we expect (need to make the client more testable)
    }


}
