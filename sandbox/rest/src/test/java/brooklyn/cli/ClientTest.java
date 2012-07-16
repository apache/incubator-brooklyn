package brooklyn.cli;

import brooklyn.rest.BrooklynService;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

public class ClientTest {

    BrooklynService brooklynServer;
    Client brooklynClient;

    private ByteArrayOutputStream outBytes;
    private PrintStream out;
    private ByteArrayOutputStream errBytes;
    private PrintStream err;

    protected String standardOut() {
        return outBytes.toString();
    }

    protected String standardErr() {
        return errBytes.toString();
    }

    @BeforeClass
    public void testSetUp() throws Exception {
        // Start the REST server
        brooklynServer = BrooklynService.newBrooklynService();
        String[] args = {"server","config.sample.yml"};
        brooklynServer.runAsync(args);
        // Rewire the output/error stream of the client
        outBytes = new ByteArrayOutputStream();
        out = new PrintStream(outBytes);
        errBytes = new ByteArrayOutputStream();
        err = new PrintStream(errBytes);
        brooklynClient = new Client(out,err);
    }

    @AfterClass
    public void testTearDown() {
        // Kill the REST server and client instance
        brooklynServer = null;
        brooklynClient = null;
        // Reset i/o streams
        outBytes = null;
        out = null;
        errBytes = null;
        err = null;
    }

    @Test(enabled = true)
    public void testCatalogEntitiesCommand() throws Exception {
        // Run the catalog-entities command
        String[] args = {"catalog-entities"};
        brooklynClient.run(args);
        // Check that output matches what we expect
        assertThat(standardOut(), containsString("brooklyn.entity.webapp.jboss.JBoss6Server"));

    }


}
