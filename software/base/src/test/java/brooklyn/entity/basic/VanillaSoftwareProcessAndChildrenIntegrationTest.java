package brooklyn.entity.basic;

import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.SoftwareProcess.ChildStartableMode;
import brooklyn.entity.basic.VanillaSoftwareProcess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.Location;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.ResourceUtils;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.os.Os;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

public class VanillaSoftwareProcessAndChildrenIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(VanillaSoftwareProcessAndChildrenIntegrationTest.class);
    
    private TestApplication app;
    private Location localhost;

    private VanillaSoftwareProcess p1;
    private VanillaSoftwareProcess p2;

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        localhost = app.getManagementContext().getLocationRegistry().resolve("localhost");
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test(groups = "Integration")
    public void testModeNone() {
        prep(ChildStartableMode.NONE);        
        app.start(Collections.singleton(localhost));

        Assert.assertNotNull(p1.getAttribute(SoftwareProcess.RUN_DIR));
        Assert.assertNull(p2.getAttribute(SoftwareProcess.RUN_DIR));
    }

    @Test(groups = "Integration")
    public void testModeForeground() {
        prep(ChildStartableMode.FOREGROUND);        
        app.start(Collections.singleton(localhost));

        long timediff = timediff();
        Assert.assertTrue( Math.abs(timediff) <= 1, "should have started concurrently, not with time difference "+timediff+" ("+p1+", "+p2+")" );
    }

    @Test(groups = "Integration")
    public void testModeForegroundLate() {
        prep(ChildStartableMode.FOREGROUND_LATE);        
        app.start(Collections.singleton(localhost));

        long timediff = timediff();
        Assert.assertTrue( Math.abs(timediff) >= 2, "should have started later, not with time difference "+timediff+" ("+p1+", "+p2+")" );
    }

    @Test(groups = "Integration")
    public void testModeBackground() {
        prep(ChildStartableMode.BACKGROUND);
        
        app.start(Collections.singleton(localhost));

        checkChildComesUpSoon();
        
        long timediff = timediff();
        Assert.assertTrue( Math.abs(timediff) <= 1, "should have started concurrently, not with time difference "+timediff+" ("+p1+", "+p2+")" );
        
        // just to prevent warnings
        waitForBackgroundedTasks();
        app.stop();
        waitForBackgroundedTasks();
        app = null;
    }

    @Test(groups = "Integration")
    public void testModeBackgroundLate() {
        prep(ChildStartableMode.BACKGROUND_LATE);        
        app.start(Collections.singleton(localhost));

        checkChildNotUpYet();
        checkChildComesUpSoon();
        
        long timediff = timediff();
        Assert.assertTrue( Math.abs(timediff) >= 2, "should have started later, not with time difference "+timediff+" ("+p1+", "+p2+")" );
        
        // just to prevent warnings
        waitForBackgroundedTasks();
        app.stop();
        waitForBackgroundedTasks();
        app = null;
    }

    private void waitForBackgroundedTasks() {
        // child task is backgrounded; quick and dirty way to make sure it finishes (after setting service_up)
        Time.sleep(Duration.seconds(2));
    }

    private void checkChildNotUpYet() {
        Assert.assertFalse(p2.getAttribute(SoftwareProcess.SERVICE_UP));
    }

    private void checkChildComesUpSoon() {
        Assert.assertTrue( Entities.submit(app, DependentConfiguration.attributeWhenReady(p2, Attributes.SERVICE_UP)).getUnchecked(Duration.TEN_SECONDS) );
    }

    private long timediff() {
        Assert.assertNotNull(p1.getAttribute(SoftwareProcess.RUN_DIR));
        Assert.assertNotNull(p2.getAttribute(SoftwareProcess.RUN_DIR));
        
        Long d1 = Long.parseLong( Strings.getFirstWordAfter(new ResourceUtils(this).getResourceAsString(Os.mergePaths(p1.getAttribute(SoftwareProcess.RUN_DIR), "DATE")), "utc") );
        Long d2 = Long.parseLong( Strings.getFirstWordAfter(new ResourceUtils(this).getResourceAsString(Os.mergePaths(p2.getAttribute(SoftwareProcess.RUN_DIR), "DATE")), "utc") );

        log.info("timestamps for "+JavaClassNames.callerNiceClassAndMethod(1)+" have difference "+(d2-d1));

        return d2 - d1;
    }

    private void prep(ChildStartableMode mode) {
        String cmd = "echo utc `date +%s` > DATE ; echo human `date` >> DATE ; "
            + "{ nohup sleep 60 & } ; echo $! > $PID_FILE ; sleep 3";
        p1 = app.createAndManageChild(EntitySpec.create(VanillaSoftwareProcess.class)
            .configure(VanillaSoftwareProcess.LAUNCH_COMMAND, cmd)
            .configure(VanillaSoftwareProcess.CHILDREN_STARTABLE_MODE, mode)
            );
        p2 = p1.addChild(EntitySpec.create(VanillaSoftwareProcess.class)
            .configure(VanillaSoftwareProcess.LAUNCH_COMMAND, cmd));
        Entities.manage(p2);
        
        log.info("testing "+JavaClassNames.callerNiceClassAndMethod(1)+", using "+p1+" and "+p2);
    }

}
