package brooklyn.cli.commands;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DeployCommandTest {

    DeployCommand cmd;

    @BeforeClass
    public void oneTimeSetUp() {
        cmd = new DeployCommand();
    }

    @AfterClass
    public void oneTimeTearDown(){
        cmd = null;
    }

    @Test(enabled = true)
    public void testInferFormatClass() throws Exception {
        String inferredCommand = cmd.inferAppFormat("brooklyn.cli.MyTestApp");
        assertEquals(inferredCommand, DeployCommand.CLASS_FORMAT);
    }

    @Test(enabled = true)
    public void testInferFormatGroovy() throws Exception {
        String inferredCommand = cmd.inferAppFormat("/my/path/my.groovy");
        assertEquals(inferredCommand, DeployCommand.GROOVY_FORMAT);
    }

    @Test(enabled = true)
    public void testInferFormatJson() throws Exception {
        String inferredCommand1 = cmd.inferAppFormat("/my/path/my.json");
        assertEquals(inferredCommand1, DeployCommand.JSON_FORMAT);
        String inferredCommand2 = cmd.inferAppFormat("/my/path/my.jsn");
        assertEquals(inferredCommand2, DeployCommand.JSON_FORMAT);
    }

}
