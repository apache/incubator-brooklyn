package brooklyn.cli;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Collection;

import groovy.lang.GroovyClassLoader;

import org.iq80.cli.Cli;
import org.iq80.cli.ParseException;
import org.testng.annotations.Test;

import brooklyn.cli.Main.BrooklynCommand;
import brooklyn.cli.Main.HelpCommand;
import brooklyn.cli.Main.LaunchCommand;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.util.ResourceUtils;

import com.google.common.collect.ImmutableList;

public class CliTest {

    @Test
    public void testLoadApplicationFromClasspath() throws Exception {
        LaunchCommand launchCommand = new Main.LaunchCommand();
        ResourceUtils resourceUtils = new ResourceUtils(this);
        GroovyClassLoader loader = new GroovyClassLoader(CliTest.class.getClassLoader());
        String appName = ExampleApp.class.getName();
        Object app = launchCommand.loadApplicationFromClasspathOrParse(resourceUtils, loader, appName);
        assertTrue(app instanceof ExampleApp, "app="+app);
    }

    @Test
    public void testLoadApplicationBuilderFromClasspath() throws Exception {
        LaunchCommand launchCommand = new Main.LaunchCommand();
        ResourceUtils resourceUtils = new ResourceUtils(this);
        GroovyClassLoader loader = new GroovyClassLoader(CliTest.class.getClassLoader());
        String appName = ExampleAppBuilder.class.getName();
        Object appBuilder = launchCommand.loadApplicationFromClasspathOrParse(resourceUtils, loader, appName);
        assertTrue(appBuilder instanceof ExampleAppBuilder, "app="+appBuilder);
    }

    @Test
    public void testLoadApplicationByParsingFile() throws Exception {
        LaunchCommand launchCommand = new Main.LaunchCommand();
        ResourceUtils resourceUtils = new ResourceUtils(this);
        GroovyClassLoader loader = new GroovyClassLoader(CliTest.class.getClassLoader());
        String appName = "ExampleAppInFile.groovy"; // file found in src/test/resources (contains empty app)
        Object app = launchCommand.loadApplicationFromClasspathOrParse(resourceUtils, loader, appName);
        assertTrue(app.getClass().getName().equals("ExampleAppInFile"), "app="+app);
    }
    
    @Test
    public void testStartAndStopAllApplications() throws Exception {
        LaunchCommand launchCommand = new Main.LaunchCommand();
        Location loc = new SimulatedLocation();
        ExampleApp app = new ExampleApp();
        Entities.startManagement(app);
        
        launchCommand.startAllApps(ImmutableList.of(app), ImmutableList.of(loc));
        assertTrue(app.running);
        
        launchCommand.stopAllApps(ImmutableList.of(app));
        assertFalse(app.running);
    }
    
    @Test
    public void testWaitsForInterrupt() throws Exception {
        final LaunchCommand launchCommand = new Main.LaunchCommand();
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                launchCommand.waitUntilInterrupted();
            }});
        
        t.start();
        t.join(100);
        assertTrue(t.isAlive());
        
        t.interrupt();
        t.join(10*1000);
        assertFalse(t.isAlive());
    }

    @Test
    public void testLaunchCommand() throws ParseException {
        Cli<BrooklynCommand> cli = Main.buildCli();
        BrooklynCommand command = cli.parse("launch", "--app", "my.App", "--location", "localhost");
        assertTrue(command instanceof LaunchCommand, ""+command);
        String details = command.toString();
        assertTrue(details.contains("app=my.App"), details);   
        assertTrue(details.contains("script=null"), details);
        assertTrue(details.contains("location=localhost"), details);
        assertTrue(details.contains("port=8081"), details);
        assertTrue(details.contains("noConsole=false"), details);
        assertTrue(details.contains("noShutdwonOnExit=false"), details);
    }

    @Test
    public void testAppOptionIsOptional() throws ParseException {
        Cli<BrooklynCommand> cli = Main.buildCli();
        cli.parse("launch", "blah", "my.App");
    }
    
    public void testHelpCommand() {
        Cli<BrooklynCommand> cli = Main.buildCli();
        BrooklynCommand command = cli.parse("help");
        assertTrue(command instanceof HelpCommand);
        command = cli.parse();
        assertTrue(command instanceof HelpCommand);
    }
    
    // An empty app to be used for testing
    @SuppressWarnings("serial")
    public static class ExampleApp extends AbstractApplication {
        volatile boolean running;
        
        @Override public void start(Collection<? extends Location> locations) {
            super.start(locations);
            running = true;
        }
        @Override public void stop() {
            super.stop();
            running = false;
        }
    }
    
    // An empty app builder to be used for testing
    public static class ExampleAppBuilder extends ApplicationBuilder {
        @Override protected void doBuild() {
            // no-op
        }
    }
}
