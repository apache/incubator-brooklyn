package brooklyn.entity.chef;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.stream.StreamGobbler;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

/** Many tests expect knife on the path, but none require any configuration beyond that.
 * They will use the Brooklyn registered account (which has been set up with mysql cookbooks and more).
 * <p>
 * Note this is a free account so cannot manage many nodes. 
 * You can use the credentials in src/test/resources/hosted-chef-brooklyn-credentials/
 * to log in and configure the settings for our tests using knife. You can also log in at:
 * <p>
 * https://manage.opscode.com/
 * <p>
 * with credentials for those with need to know (which is a lot of people, but not everyone
 * with access to this github repo!).
 * <p>
 * You can easily set up your own new account, for free; download the starter kit and
 * point {@link ChefConfig#KNIFE_CONFIG_FILE} at the knife.rb.
 * <p>
 * Note that if you are porting an existing machine to be managed by a new chef account, you may need to do the following:
 * <p>
 * ON management machine:
 * <li>knife client delete HOST   # or bulk delete, but don't delete your validator! it is a PITA recreating and adding back all the permissions! 
 * <li>knife node delete HOST
 * <p>
 * ON machine being managed:
 * <li>rm -rf /{etc,var}/chef
 * <p>
 * Note also that some tests require a location  named:ChefLive  to be set up in your brooklyn.properties.
 * This can be a cloud (but will require frequent chef-node pruning) or a permanently set-up machine.
 **/
public class ChefServerTasksIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ChefServerTasksIntegrationTest.class);
    
    protected TestApplication app;
    protected ManagementContext mgmt;

    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        mgmt = app.getManagementContext();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (mgmt != null) Entities.destroyAll(mgmt);
        mgmt = null;
    }

    /** @deprecated use {@link ChefLiveTestSupport} */
    @Deprecated
    public synchronized static String installBrooklynChefHostedConfig() {
        return ChefLiveTestSupport.installBrooklynChefHostedConfig();
    }
    
    @Test(groups="Integration")
    @SuppressWarnings("resource")
    public void testWhichKnife() throws IOException, InterruptedException {
        // requires that knife is installed on the path of login shells
        Process p = Runtime.getRuntime().exec(new String[] { "bash", "-l", "-c", "which knife" });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new StreamGobbler(p.getInputStream(), out, log).start();
        new StreamGobbler(p.getErrorStream(), out, log).start();
        log.info("bash -l -c 'which knife' gives exit code: "+p.waitFor());
        Time.sleep(Duration.millis(1000));
        log.info("output:\n"+out);
        Assert.assertEquals(p.exitValue(), 0);
    }

    @Test(groups="Integration")
    public void testKnifeWithoutConfig() {
        // without config it shouldn't pass
        // (assumes that knife global config is *not* installed on your machine)
        ProcessTaskWrapper<Boolean> t = Entities.submit(app, ChefServerTasks.isKnifeInstalled());
        log.info("isKnifeInstalled without config returned: "+t.get()+" ("+t.getExitCode()+")\n"+t.getStdout()+"\nERR:\n"+t.getStderr());
        Assert.assertFalse(t.get());
    }

    @Test(groups="Integration")
    public void testKnifeWithConfig() {
        // requires that knife is installed on the path of login shells
        // (creates the config in a temp space)
        ChefLiveTestSupport.installBrooklynChefHostedConfig(app);
        ProcessTaskWrapper<Boolean> t = Entities.submit(app, ChefServerTasks.isKnifeInstalled());
        log.info("isKnifeInstalled *with* config returned: "+t.get()+" ("+t.getExitCode()+")\n"+t.getStdout()+"\nERR:\n"+t.getStderr());
        Assert.assertTrue(t.get());
    }

}
