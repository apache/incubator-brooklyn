package brooklyn.util.internal.ssh.process;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.util.collections.MutableMap;

public class ProcessToolStaticsTest {

    ByteArrayOutputStream out;
    ByteArrayOutputStream err;
    
    @BeforeMethod(alwaysRun=true)
    public void clear() {
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
    }
    
    @Test
    public void testRunsWithStdout() throws Exception {
        int code = ProcessTool.execSingleProcess(Arrays.asList("echo", "hello", "world"), null, out, err, this);
        Assert.assertEquals(err.toString().trim(), "");
        Assert.assertEquals(out.toString().trim(), "hello world");
        Assert.assertEquals(code, 0);
    }

    @Test(groups="Integration") // *nix only
    public void testRunsWithBashEnvVarAndStderr() throws Exception {
        int code = ProcessTool.execSingleProcess(Arrays.asList("/bin/bash", "-c", "echo hello $NAME | tee /dev/stderr"), 
                MutableMap.of("NAME", "BOB"), out, err, this);
        Assert.assertEquals(err.toString().trim(), "hello BOB", "err is: "+err);
        Assert.assertEquals(out.toString().trim(), "hello BOB", "out is: "+out);
        Assert.assertEquals(code, 0);
    }

    @Test(groups="Integration") // *nix only
    public void testRunsManyCommandsWithBashEnvVarAndStderr() throws Exception {
        int code = ProcessTool.execProcesses(Arrays.asList("echo hello $NAME", "export NAME=JOHN", "echo goodbye $NAME | tee /dev/stderr"), 
                MutableMap.of("NAME", "BOB"), out, err, " ; ", this);
        Assert.assertEquals(err.toString().trim(), "goodbye JOHN", "err is: "+err);
        Assert.assertEquals(out.toString().trim(), "hello BOB\ngoodbye JOHN", "out is: "+out);
        Assert.assertEquals(code, 0);
    }


}
