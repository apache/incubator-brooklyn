package brooklyn.cli;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import groovy.lang.GroovyClassLoader;

import java.util.Collection;

import org.iq80.cli.Cli;
import org.iq80.cli.ParseException;
import org.testng.annotations.Test;

import brooklyn.cli.Main.BrooklynCommand;
import brooklyn.cli.Main.HelpCommand;
import brooklyn.cli.Main.LaunchCommand;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntityProxy;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.util.ResourceUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class CliTest {

    @Test
    public void testLoadApplicationFromClasspath() throws Exception {
        String appName = ExampleApp.class.getName();
        Object app = loadApplicationFromClasspathOrParse(appName);
        assertTrue(app instanceof ExampleApp, "app="+app);
    }

    @Test
    public void testLoadApplicationBuilderFromClasspath() throws Exception {
        String appName = ExampleAppBuilder.class.getName();
        Object appBuilder = loadApplicationFromClasspathOrParse(appName);
        assertTrue(appBuilder instanceof ExampleAppBuilder, "app="+appBuilder);
    }

    @Test
    public void testLoadEntityFromClasspath() throws Exception {
        String entityName = ExampleEntity.class.getName();
        Object appBuilder = loadApplicationFromClasspathOrParse(entityName);
        assertTrue(appBuilder instanceof ApplicationBuilder, "app="+appBuilder);
        
        StartableApplication app = ((ApplicationBuilder)appBuilder).manage();
        Collection<Entity> entities = app.getChildren();
        assertEquals(entities.size(), 1, "entities="+entities);
        assertTrue(Iterables.getOnlyElement(entities) instanceof ExampleEntity, "entities="+entities);
        assertTrue(Iterables.getOnlyElement(entities) instanceof EntityProxy, "entities="+entities);
    }

    @Deprecated // Tests deprecated approach of using impl directly
    @Test
    public void testLoadEntityImplFromClasspath() throws Exception {
        String entityName = ExampleEntityImpl.class.getName();
        Object appBuilder = loadApplicationFromClasspathOrParse(entityName);
        assertTrue(appBuilder instanceof ApplicationBuilder, "app="+appBuilder);
        
        StartableApplication app = ((ApplicationBuilder)appBuilder).manage();
        Collection<Entity> entities = app.getChildren();
        assertEquals(entities.size(), 1, "entities="+entities);
        assertEquals(Iterables.getOnlyElement(entities).getEntityType().getName(), ExampleEntityImpl.class.getCanonicalName(), "entities="+entities);
        assertTrue(Iterables.getOnlyElement(entities) instanceof EntityProxy, "entities="+entities);
    }

    @Test
    public void testLoadApplicationByParsingFile() throws Exception {
        String appName = "ExampleAppInFile.groovy"; // file found in src/test/resources (contains empty app)
        Object app = loadApplicationFromClasspathOrParse(appName);
        assertTrue(app.getClass().getName().equals("ExampleAppInFile"), "app="+app);
    }
    
    private Object loadApplicationFromClasspathOrParse(String appName) throws Exception {
        LaunchCommand launchCommand = new Main.LaunchCommand();
        ResourceUtils resourceUtils = new ResourceUtils(this);
        GroovyClassLoader loader = new GroovyClassLoader(CliTest.class.getClassLoader());
        return launchCommand.loadApplicationFromClasspathOrParse(resourceUtils, loader, appName);
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
    
    // An empty entity to be used for testing
    @ImplementedBy(ExampleEntityImpl.class)
    public static interface ExampleEntity extends Entity, Startable {
    }

    @SuppressWarnings("serial")
    public static class ExampleEntityImpl extends AbstractEntity implements ExampleEntity {
        volatile boolean running;
        
        @Override public void start(Collection<? extends Location> locations) {
            running = true;
        }
        @Override public void stop() {
            running = false;
        }
        @Override public void restart() {
        }
    }

    // An empty app builder to be used for testing
    public static class ExampleAppBuilder extends ApplicationBuilder {
        @Override protected void doBuild() {
            // no-op
        }
    }
}
