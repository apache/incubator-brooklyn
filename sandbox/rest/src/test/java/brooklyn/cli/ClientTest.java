package brooklyn.cli;

import brooklyn.rest.BrooklynService;
import brooklyn.rest.core.ApplicationManager;
import com.google.common.collect.Iterables;
import com.yammer.dropwizard.logging.Log;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;

import static org.testng.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

public class ClientTest {

    private final static Log LOG = Log.forClass(ClientTest.class);

    private BrooklynService brooklynServer;
    private ApplicationManager applicationManager;
    private Client brooklynClient;

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
    public void setUp() throws Exception {
        // Start the REST server
        brooklynServer = BrooklynService.newBrooklynService();

        //TODO Create a temporary file for config.sample.yml, so can run in jenkins?
        //URL configUrl = getClass().getClassLoader().getResource("config/config.sample.yml");
        String[] args = {"server","config.sample.yml"};
        brooklynServer.runAsync(args);
        applicationManager = brooklynServer.getApplicationManager();
    }

    @BeforeMethod
    public void setUpBeforeMethod() throws Exception {
        // Set i/o streams
        outBytes = new ByteArrayOutputStream();
        out = new PrintStream(outBytes);
        errBytes = new ByteArrayOutputStream();
        err = new PrintStream(errBytes);
        // Create a client instance
        brooklynClient = new Client(out,err);
    }

    @AfterClass
    public void tearDown() throws Exception {
        // Kill the REST server and client instance
        brooklynServer.stop();
    }

    @Test(enabled = false)
    public void testCatalogEntitiesCommand() throws Exception {
        try {
            // Run the catalog-entities commandb
            String[] args = {"catalog-entities"};
            brooklynClient.run(args);
            // Check that output matches what we expect
            assertThat(standardOut(), containsString("brooklyn.entity.basic.BasicEntity"));
        } catch (Exception e) {
            LOG.error("\nstdout="+standardOut()+"\nstderr="+standardErr()+"\n", e);
            throw e;
        }
    }

    @Test(enabled = true)
    public void testDeployCreatesApp() throws Exception {
        try {
            // Run the deploy command
            String[] args = {"deploy","--format","class", "brooklyn.cli.ExampleApp"};
            brooklynClient.run(args);
            // We should only have 1 app in the server's registry
            assertEquals(applicationManager.registry().size(), 1);
            // The name of that app should match what we have provided in the deploy command
            assertEquals(Iterables.getOnlyElement(applicationManager.registry().keySet()), ExampleApp.class.getName());
        } catch (Exception e) {
            LOG.error("stdout="+standardOut()+"; stderr="+standardErr(), e);
            throw e;
        }
    }
}
