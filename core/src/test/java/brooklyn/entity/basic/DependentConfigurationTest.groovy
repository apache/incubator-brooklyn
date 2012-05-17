package brooklyn.entity.basic;

import java.util.concurrent.TimeUnit

import org.testng.Assert
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.event.basic.DependentConfiguration;
import brooklyn.management.ExecutionContext
import brooklyn.management.ExecutionManager;
import brooklyn.management.Task
import brooklyn.util.task.BasicExecutionContext;
import brooklyn.util.task.BasicExecutionManager
import brooklyn.util.task.BasicTask

/** Tests the standalone routines in dependent configuration.
 * See e.g. LocalEntitiesTest for tests of attributeWhenReady etc.
 */
public class DependentConfigurationTest {

    private ExecutionManager em
    private ExecutionContext ec

    @BeforeMethod
    public void setUp() {
        em = new BasicExecutionManager()
        ec = new BasicExecutionContext(em);
    }

    @Test
    public void testTransform() {
        Task t = DependentConfiguration.transform(new BasicTask({ 2 }), { it+1 });
        ec.submit(t);
        Assert.assertEquals(t.get(1, TimeUnit.SECONDS), 3);
    }

    @Test
    public void testFormatString() {
        Task t = DependentConfiguration.formatString("%s://%s:%d/",
            "http",
            new BasicTask({ "localhost" }),
            DependentConfiguration.transform(new BasicTask({ 8080 }), { it+1 }));
        ec.submit(t);
        Assert.assertEquals(t.get(1, TimeUnit.SECONDS), "http://localhost:8081/");
    }

}
