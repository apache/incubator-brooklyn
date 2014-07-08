/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.basic;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.SoftwareProcess.ChildStartableMode;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.ResourceUtils;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.os.Os;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Stopwatch;

public class VanillaSoftwareProcessAndChildrenIntegrationTest {

    // TODO Why are these tests so slow? Even when the sleep time was 3 seconds instead of 10, they would still take about 10 seconds.
    
    // Note that tests run by jenkins can be extremely time-sensitive.
    // e.g. http://brooklyn.builds.cloudsoftcorp.com/job/Brooklyn-Master-Integration/io.brooklyn$brooklyn-software-base/217/testReport/junit/brooklyn.entity.basic/VanillaSoftwareProcessAndChildrenIntegrationTest/testModeBackground/
    //      shows a 5 second difference when in background mode, whereas the test originally asserted a difference of <= 1.
    // Therefore increasing time that tests will take, but decreasing the sensitivity so we don't get such false-negatives.
    
    private static final Logger log = LoggerFactory.getLogger(VanillaSoftwareProcessAndChildrenIntegrationTest.class);

    private static final int PARENT_TASK_SLEEP_LENGTH_SECS = 10;
    private static final int CHILD_TASK_SLEEP_LENGTH_SECS = 10;
    private static final int CONCURRENT_MAX_ACCEPTABLE_DIFF_SECS = PARENT_TASK_SLEEP_LENGTH_SECS - 1;
    private static final int SEQUENCTIAL_MIN_ACCEPTABLE_DIFF_SECS = PARENT_TASK_SLEEP_LENGTH_SECS - 1;
    private static final int EARLY_RETURN_GRACE_MS = 20;
    
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
        long startTime = startApp();

        Assert.assertNotNull(p1.getAttribute(SoftwareProcess.RUN_DIR));
        Assert.assertNull(p2.getAttribute(SoftwareProcess.RUN_DIR));
        Assert.assertTrue(startTime >= PARENT_TASK_SLEEP_LENGTH_SECS*1000 - EARLY_RETURN_GRACE_MS, "startTime="+Time.makeTimeStringRounded(startTime));
    }

    @Test(groups = "Integration")
    public void testModeForeground() {
        prep(ChildStartableMode.FOREGROUND);        
        long startTime = startApp();

        long timediff = timediff();
        Assert.assertTrue( Math.abs(timediff) <= CONCURRENT_MAX_ACCEPTABLE_DIFF_SECS, "should have started concurrently, not with time difference "+timediff+" ("+p1+", "+p2+")" );
        Assert.assertTrue(startTime >= PARENT_TASK_SLEEP_LENGTH_SECS*1000 - EARLY_RETURN_GRACE_MS, "startTime="+Time.makeTimeStringRounded(startTime));
    }

    @Test(groups = "Integration")
    public void testModeForegroundLate() {
        prep(ChildStartableMode.FOREGROUND_LATE);        
        long startTime = startApp();

        long timediff = timediff();
        Assert.assertTrue( timediff >= SEQUENCTIAL_MIN_ACCEPTABLE_DIFF_SECS, "should have started later, not with time difference "+timediff+" ("+p1+", "+p2+")" );
        Assert.assertTrue(startTime >= 2*PARENT_TASK_SLEEP_LENGTH_SECS*1000 - EARLY_RETURN_GRACE_MS, "startTime="+Time.makeTimeStringRounded(startTime));
    }

    @Test(groups = "Integration")
    public void testModeBackground() {
        prep(ChildStartableMode.BACKGROUND);
        long startTime = startApp();

        checkChildComesUpSoon();
        
        long timediff = timediff();
        Assert.assertTrue( Math.abs(timediff) <= CONCURRENT_MAX_ACCEPTABLE_DIFF_SECS, "should have started concurrently, not with time difference "+timediff+" ("+p1+", "+p2+")" );
        Assert.assertTrue(startTime >= PARENT_TASK_SLEEP_LENGTH_SECS*1000 - EARLY_RETURN_GRACE_MS, "startTime="+Time.makeTimeStringRounded(startTime));
    }

    @Test(groups = "Integration")
    public void testModeBackgroundLate() {
        prep(ChildStartableMode.BACKGROUND_LATE);
        long startTime = startApp();

        checkChildNotUpYet();
        checkChildComesUpSoon();
        
        long timediff = timediff();
        Assert.assertTrue( Math.abs(timediff) >= SEQUENCTIAL_MIN_ACCEPTABLE_DIFF_SECS, "should have started later, not with time difference "+timediff+" ("+p1+", "+p2+")" );
        Assert.assertTrue(startTime >= PARENT_TASK_SLEEP_LENGTH_SECS*1000 - EARLY_RETURN_GRACE_MS, "startTime="+Time.makeTimeStringRounded(startTime));
        
        // just to prevent warnings
        waitForBackgroundedTasks(CHILD_TASK_SLEEP_LENGTH_SECS+1);
        app.stop();
        app = null;
    }
    
    private long startApp() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        app.start(Collections.singleton(localhost));
        long result = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        log.info("Took "+Time.makeTimeStringRounded(result)+" for app.start to complete");
        return result;
    }

    private void waitForBackgroundedTasks(int secs) {
        // child task is backgrounded; quick and dirty way to make sure it finishes (after setting service_up)
        Time.sleep(Duration.seconds(secs));
    }

    private void checkChildNotUpYet() {
        Assert.assertFalse(p2.getAttribute(SoftwareProcess.SERVICE_UP));
    }

    private void checkChildComesUpSoon() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        EntityTestUtils.assertAttributeEqualsEventually(p2, Attributes.SERVICE_UP, true);
        log.info("Took "+Time.makeTimeStringRounded(stopwatch)+" for child-process to be service-up");
    }

    private long timediff() {
        Long d1 = getRunTimeUtc(p1);
        Long d2 = getRunTimeUtc(p2);

        log.info("timestamps for "+JavaClassNames.callerNiceClassAndMethod(1)+" have difference "+(d2-d1));

        return d2 - d1;
    }

    private Long getRunTimeUtc(VanillaSoftwareProcess p) {
        Assert.assertNotNull(p.getAttribute(SoftwareProcess.RUN_DIR));
        return Long.parseLong( Strings.getFirstWordAfter(new ResourceUtils(this).getResourceAsString(Os.mergePaths(p.getAttribute(SoftwareProcess.RUN_DIR), "DATE")), "utc") );
    }
    
    private void prep(ChildStartableMode mode) {
        String parentCmd = "echo utc `date +%s` > DATE ; echo human `date` >> DATE ; "
            + "{ nohup sleep 60 & } ; echo $! > $PID_FILE ; sleep "+PARENT_TASK_SLEEP_LENGTH_SECS;
        
        String childCmd = "echo utc `date +%s` > DATE ; echo human `date` >> DATE ; "
                + "{ nohup sleep 60 & } ; echo $! > $PID_FILE ; sleep "+CHILD_TASK_SLEEP_LENGTH_SECS;
        
        p1 = app.createAndManageChild(EntitySpec.create(VanillaSoftwareProcess.class)
            .configure(VanillaSoftwareProcess.LAUNCH_COMMAND, parentCmd)
            .configure(VanillaSoftwareProcess.CHILDREN_STARTABLE_MODE, mode)
            );
        p2 = p1.addChild(EntitySpec.create(VanillaSoftwareProcess.class)
            .configure(VanillaSoftwareProcess.LAUNCH_COMMAND, childCmd));
        Entities.manage(p2);
        
        log.info("testing "+JavaClassNames.callerNiceClassAndMethod(1)+", using "+p1+" and "+p2);
    }

}
