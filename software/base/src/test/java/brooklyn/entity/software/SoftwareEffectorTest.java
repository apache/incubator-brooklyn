package brooklyn.entity.software;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Effector;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.software.SshEffectorTasks.SshEffectorBody;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.task.ssh.SshExecTaskWrapper;

import com.google.common.base.Throwables;

public class SoftwareEffectorTest {

    private static final Logger log = LoggerFactory.getLogger(SoftwareEffectorTest.class);
                
    TestApplication app;
    ManagementContext mgmt;
    
    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        mgmt = app.getManagementContext();
        
        LocalhostMachineProvisioningLocation lhc = mgmt.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
        SshMachineLocation lh = lhc.obtain();
        app.start(Arrays.asList(lh));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (mgmt != null) Entities.destroyAll(mgmt);
        mgmt = null;
    }

    public static final Effector<String> GET_REMOTE_DATE_1 = Effectors.effector(String.class, "getRemoteDate")
            .description("retrieves the date from the remote machine")
            .impl(new SshEffectorBody<String>() {
                public String call(ConfigBag parameters) {
                    queue( ssh("date").requiringZeroAndReturningStdout() );
                    return last(String.class);
                }
            })
            .build();

    public static final Effector<String> GET_REMOTE_DATE_2 = Effectors.effector(GET_REMOTE_DATE_1)
            // Just get year to confirm implementation is different
            .description("retrieves the year from the remote machine")
            .impl(SshEffectorTasks.ssh("date +%Y").requiringZeroAndReturningStdout())
            .build();

    // TODO revisit next two tests before end 2019 ;)

    @Test(groups="Integration")
    public void testSshDateEffector1() {
        Task<String> call = Entities.invokeEffector(app, app, GET_REMOTE_DATE_1);
        log.info("ssh date 1 gives: "+call.getUnchecked());
        Assert.assertTrue(call.getUnchecked().indexOf("201") > 0);
    }

    @Test(groups="Integration")
    public void testSshDateEffector2() {
        Task<String> call = Entities.invokeEffector(app, app, GET_REMOTE_DATE_2);
        log.info("ssh date 2 gives: "+call.getUnchecked());
        Assert.assertTrue(call.getUnchecked().indexOf("201") == 0);
    }

    public static final String COMMAND_THAT_DOES_NOT_EXIST = "blah_blah_blah_command_DOES_NOT_EXIST";
    
    @Test(groups="Integration")
    public void testBadExitCodeCaught() {
        Task<Void> call = Entities.invokeEffector(app, app, Effectors.effector(Void.class, "badExitCode")
                .impl(new SshEffectorBody<Void>() {
                    public Void call(ConfigBag parameters) {
                        queue( ssh(COMMAND_THAT_DOES_NOT_EXIST).requiringZeroAndReturningStdout() );
                        return null;
                    }
                }).build() );
        try {
            Object result = call.getUnchecked();
            Assert.fail("ERROR: should have failed earlier in this test, instead got successful task result "+result+" from "+call);
        } catch (Exception e) {
            Throwable root = Throwables.getRootCause(e);
            if (!(root instanceof IllegalStateException)) Assert.fail("Should have failed with IAE, but got: "+root);
            if (root.getMessage()==null || root.getMessage().indexOf("exit code")<=0) 
                Assert.fail("Should have failed with 'exit code' message, but got: "+root);
            // test passed
            return;
        }
    }
        
    @Test(groups="Integration")
    public void testBadExitCodeCaughtAndStdErrAvailable() {
        final SshExecTaskWrapper<?>[] sshTasks = new SshExecTaskWrapper[1];
        
        Task<Void> call = Entities.invokeEffector(app, app, Effectors.effector(Void.class, "badExitCode")
                .impl(new SshEffectorBody<Void>() {
                    public Void call(ConfigBag parameters) {
                        sshTasks[0] = queue( ssh(COMMAND_THAT_DOES_NOT_EXIST).requiringExitCodeZero() );
                        return null;
                    }
                }).build() );
        call.blockUntilEnded();
        Assert.assertTrue(call.isError());
        log.info("stderr gives: "+new String(sshTasks[0].getStderr()));
        Assert.assertTrue(new String(sshTasks[0].getStderr()).indexOf(COMMAND_THAT_DOES_NOT_EXIST) >= 0);
    }
        
}
