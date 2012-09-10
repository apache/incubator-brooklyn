package brooklyn.entity.basic.lifecycle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

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
                commands.addAll(script);
                return result;
            }
        };
    }
    
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
    
//    public void testFailWithErrorMessage() {
//        def script = new ScriptHelper(newMockRunner(0), "mock").
//                body.append(
//                    or("wget url", echo("### BROOKLYN 1234 ###  can't download wget")),
//                    "cd /tmp/mydir",
//                    "sudo mycommand",
//                    onFailure("./configure", echo("can't download wget")
//                 ).
//                grepStdout("### BROOKLYN ###")
//                    
//    }

    
}
