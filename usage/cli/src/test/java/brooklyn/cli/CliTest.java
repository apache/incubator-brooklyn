package brooklyn.cli;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import groovy.lang.GroovyClassLoader;

import org.iq80.cli.Cli;
import org.iq80.cli.ParseException;
import org.testng.annotations.Test;

import brooklyn.cli.Main.BrooklynCommand;
import brooklyn.cli.Main.LaunchCommand;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.util.ResourceUtils;

public class CliTest {

    //TODO: add another test like this one, but which which loads the class
    //      from a .groovy file (DO NOT call it ExampleApp)
    @Test
    public void testClassloadsApplication() throws Exception {
        LaunchCommand launchCommand = new Main.LaunchCommand();
        ResourceUtils resourceUtils = new ResourceUtils(this);
        GroovyClassLoader loader = new GroovyClassLoader(CliTest.class.getClassLoader());
        String appName = ExampleApp.class.getName();
        
        AbstractApplication app = launchCommand.loadApplicationFromClasspathOrParse(resourceUtils, loader, appName);
        assertTrue(app instanceof ExampleApp, "app="+app);
    }
    
    @Test
    public void testLaunchCommand() throws ParseException {
        Cli<BrooklynCommand> cli = Main.getCli();
        BrooklynCommand command = cli.parse("launch", "--app", "my.App");
        assertTrue(command instanceof LaunchCommand);
        String details = command.toString();
        assertTrue(details.contains("app=my.App"));
        //TODO: add more assertions
    }

    @Test(expectedExceptions = ParseException.class, expectedExceptionsMessageRegExp = "Required option '-a' is missing")
    public void testMissingAppOption() throws ParseException {
        Cli<BrooklynCommand> cli = Main.getCli();
        cli.parse("launch", "blah", "my.App");
        fail("Should throw ParseException");
    }
    
    //TODO: add tests for the HelpCommand
    
    @SuppressWarnings("serial")
    public static class ExampleApp extends AbstractApplication {}
    
}
