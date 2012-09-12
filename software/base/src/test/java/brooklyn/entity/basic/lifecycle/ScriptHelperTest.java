package brooklyn.entity.basic.lifecycle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Throwables;

import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.util.MutableMap;

@Test
public class ScriptHelperTest {
    
    private static final Logger log = LoggerFactory.getLogger(ScriptHelperTest.class);
    
    List<String> commands = new ArrayList<String>();
    
    @BeforeMethod
    private void setup() { commands.clear(); }
    
    private ScriptRunner newMockRunner(final int result) {
        return new ScriptRunner() {
            @Override
            public int execute(List<String> script, String summaryForLogging) {
                return execute(new MutableMap(), script, summaryForLogging);
            }
            @Override
            public int execute(Map flags, List<String> script, String summaryForLogging) {
                commands.addAll(script);
                return result;                
            }
        };
    }

    public static ScriptRunner newLocalhostRunner() {
        return new ScriptRunner() {
            LocalhostMachineProvisioningLocation location = new LocalhostMachineProvisioningLocation();
            @Override
            public int execute(List<String> script, String summaryForLogging) {
                return execute(new MutableMap(), script, summaryForLogging);
            }
            @Override
            public int execute(Map flags, List<String> script, String summaryForLogging) {
                try {
                    Map flags2 = MutableMap.of("logPrefix", "test");
                    flags2.putAll(flags);
                    return location.obtain().execScript(flags2, summaryForLogging, script);
                } catch (NoMachinesAvailableException e) {
                    throw Throwables.propagate(e);
                }
            }        
        };
    };

    public void testHeadBodyFootAndResult() {
        ScriptHelper h = new ScriptHelper(newMockRunner(101), "mock");
        int result = h.header.
                append("h1", "h2").body.append("b1", "b2").footer.append("f1", "f2").
                execute();
        Assert.assertEquals(result, 101);
        Assert.assertEquals(commands, Arrays.asList("h1", "h2", "b1", "b2", "f1", "f2"), "List wrong: "+commands);
    }

    public void testFailOnNonZero() {
        ScriptHelper h = new ScriptHelper(newMockRunner(106), "mock");
        boolean succeededWhenShouldntHave = false;
        try {
            h.body.append("ignored").
                failOnNonZeroResultCode().   // will happen
                execute();            
            succeededWhenShouldntHave = true;
        } catch (Exception e) {
            log.info("ScriptHelper non-zero causes return code: "+e);
        }
        if (succeededWhenShouldntHave) Assert.fail("succeeded when shouldn't have");
    }

    public void testFailOnNonZeroDontFailIfZero() {
        int result = new ScriptHelper(newMockRunner(0), "mock").body.append("ignored").
                failOnNonZeroResultCode().   // will happen
                execute();
        Assert.assertEquals(result, 0);
    }


    @Test(groups = "Integration")
    public void testFailingCommandFailsEarly() {
        ScriptHelper script = new ScriptHelper(newLocalhostRunner(), "mock").
                body.append("curl road://to/nowhere", "exit 11").
                gatherOutput();
        int result = script.execute();
        // should get _1_ from curl failing, not 11 from us
        // TODO not sure why though!
        Assert.assertEquals(1, result);
    }


    // TODO ALEX it would be cool to use well-known tokens to streamline output from scripts
    // but i'm not seeing any simple way to do that,
    // either for supplying better error messages, or selecting output we show somewhere else.
    // eventually we might have a process log which would let us dial up/down and step through executions
    
    // specific motivation atm is nginx failures, and ssh localhost failures.
    // i am putting better errors in those relevant (targeted) code, when they fail, 
    // **they** provide lots of user help.  not ideal but better than a lot of code
    // which we're not sure we're going to want
    
    // TODO another motivation (deferred for now) is saying when downloads fail, as that is quite a common case
    // but i think we need quite a bit of scaffolding to detect that problem (without inspecting logs) ...

    @Test(groups = "Integration")
    public void testGatherOutputStdout() {
        ScriptHelper script = new ScriptHelper(newLocalhostRunner(), "mock").
                body.append("echo `echo foo``echo bar`", "exit 8").
                gatherOutput();
        int result = script.execute();
        Assert.assertEquals(8, result);
        if (!script.getResultStdout().contains("foobar"))
            Assert.fail("STDOUT does not contain expected text 'foobar'.\n"+script.getResultStdout()+
                    "\nSTDERR:\n"+script.getResultStderr());
    }

    @Test(groups = "Integration")
    public void testGatherOutputStderr() {
        ScriptHelper script = new ScriptHelper(newLocalhostRunner(), "mock").
                body.append("set -x", "curl road://to/nowhere || exit 11").
                gatherOutput();
        int result = script.execute();
        Assert.assertEquals(11, result);
        if (!script.getResultStderr().contains("road"))
            Assert.fail("STDERR does not contain expected text 'road'.\n"+script.getResultStderr()+
                    "\nSTDOUT:\n"+script.getResultStdout());
    }

    @Test(groups = "Integration")
    public void testGatherOutuputNotEnabled() {
        ScriptHelper script = new ScriptHelper(newLocalhostRunner(), "mock").
                body.append("echo foo", "exit 11");
        int result = script.execute();
        Assert.assertEquals(11, result);
        boolean succeededWhenShouldNotHave = false;
        try {
            script.getResultStdout();
            succeededWhenShouldNotHave = true;
        } catch (Exception e) { /* expected */ }
        if (succeededWhenShouldNotHave) Assert.fail("Should have failed");
    }

}
