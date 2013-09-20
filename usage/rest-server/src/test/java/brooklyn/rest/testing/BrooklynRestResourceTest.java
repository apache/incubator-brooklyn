package brooklyn.rest.testing;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import brooklyn.rest.domain.Status;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import brooklyn.rest.domain.ApplicationSummary;
import brooklyn.util.internal.Repeater;

import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.spi.inject.Errors;

public abstract class BrooklynRestResourceTest extends BrooklynRestApiTest {

    @BeforeClass(alwaysRun = true)
    public void setUp() throws Exception {
        // need this to debug jersey inject errors
        java.util.logging.Logger.getLogger(Errors.class.getName()).setLevel(Level.INFO);

        setUpJersey();
    }

    @AfterClass(alwaysRun = false)
    public void tearDown() throws Exception {
        tearDownJersey();
        super.tearDown();
    }

    protected void waitForApplicationToBeRunning(final URI applicationRef) {
        boolean started = Repeater.create("Wait for application startup")
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        Status status = getApplicationStatus(applicationRef);
                        if (status == Status.ERROR) {
                            fail("Application failed with ERROR");
                        }
                        return status == Status.RUNNING;
                    }
                })
                .every(10, TimeUnit.SECONDS)
                .limitTimeTo(3, TimeUnit.MINUTES)
                .run();
        assertTrue(started);
    }

    protected Status getApplicationStatus(URI uri) {
        return client().resource(uri).get(ApplicationSummary.class).getStatus();
    }

    protected void waitForPageNotFoundResponse(final String resource, final Class<?> clazz) {
        boolean found = Repeater.create("Wait for page not found")
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        try {
                            client().resource(resource).get(clazz);
                            return false;
                        } catch (UniformInterfaceException e) {
                            return e.getResponse().getStatus() == 404;
                        }
                    }
                })
                .every(1, TimeUnit.SECONDS)
                .limitTimeTo(30, TimeUnit.SECONDS)
                .run();
        assertTrue(found);
    }
}
