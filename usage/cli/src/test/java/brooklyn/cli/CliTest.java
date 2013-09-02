package brooklyn.cli;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import groovy.lang.GroovyClassLoader;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.iq80.cli.Cli;
import org.iq80.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
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
import brooklyn.test.TestUtils;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class CliTest {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractEntity.class);

    private ExecutorService executor;
    private StartableApplication app;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        executor = Executors.newCachedThreadPool();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (executor != null) executor.shutdownNow();
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testLoadApplicationFromClasspath() throws Exception {
        String appName = ExampleApp.class.getName();
        Object appBuilder = loadApplicationFromClasspathOrParse(appName);
        assertTrue(appBuilder instanceof ApplicationBuilder, "app="+appBuilder);
        assertAppWrappedInBuilder((ApplicationBuilder)appBuilder, ExampleApp.class.getCanonicalName());
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
        
        app = ((ApplicationBuilder)appBuilder).manage();
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
        
        app = ((ApplicationBuilder)appBuilder).manage();
        Collection<Entity> entities = app.getChildren();
        assertEquals(entities.size(), 1, "entities="+entities);
        assertEquals(Iterables.getOnlyElement(entities).getEntityType().getName(), ExampleEntity.class.getCanonicalName(), "entities="+entities);
        assertTrue(Iterables.getOnlyElement(entities) instanceof EntityProxy, "entities="+entities);
    }

    @Test
    public void testLoadApplicationByParsingFile() throws Exception {
        String appName = "ExampleAppInFile.groovy"; // file found in src/test/resources (contains empty app)
        Object appBuilder = loadApplicationFromClasspathOrParse(appName);
        assertTrue(appBuilder instanceof ApplicationBuilder, "app="+appBuilder);
        assertAppWrappedInBuilder((ApplicationBuilder)appBuilder, "ExampleAppInFile");
    }
    
    private Object loadApplicationFromClasspathOrParse(String appName) throws Exception {
        LaunchCommand launchCommand = new Main.LaunchCommand();
        ResourceUtils resourceUtils = new ResourceUtils(this);
        GroovyClassLoader loader = new GroovyClassLoader(CliTest.class.getClassLoader());
        return launchCommand.loadApplicationFromClasspathOrParse(resourceUtils, loader, appName);
    }
    
    private void assertAppWrappedInBuilder(ApplicationBuilder builder, String expectedAppTypeName) {
        StartableApplication app = builder.manage();
        try {
            String typeName = app.getEntityType().getName();
            assertEquals(typeName, expectedAppTypeName, "app="+app+"; typeName="+typeName);
        } finally {
            Entities.destroyAll(app.getManagementContext());
        }
    }
    
    public static final AtomicBoolean GROOVY_INVOKED = new AtomicBoolean(false);
    @Test
    public void testInvokeGroovyScript() throws Exception {
        File groovyFile = File.createTempFile("testinvokegroovy", "groovy");
        try {
            String contents = CliTest.class.getCanonicalName()+".GROOVY_INVOKED.set(true);";
            Files.write(contents.getBytes(), groovyFile);

            LaunchCommand launchCommand = new Main.LaunchCommand();
            ResourceUtils resourceUtils = new ResourceUtils(this);
            GroovyClassLoader loader = new GroovyClassLoader(CliTest.class.getClassLoader());
            launchCommand.execGroovyScript(resourceUtils, loader, groovyFile.toURI().toString());
            assertTrue(GROOVY_INVOKED.get());
            
        } finally {
            groovyFile.delete();
            GROOVY_INVOKED.set(false);
        }
    }
    
    @Test
    public void testStopAllApplications() throws Exception {
        LaunchCommand launchCommand = new Main.LaunchCommand();
        ExampleApp app = new ExampleApp();
        try {
            Entities.startManagement(app);
            app.start(ImmutableList.of(new SimulatedLocation()));
            assertTrue(app.running);
            
            launchCommand.stopAllApps(ImmutableList.of(app));
            assertFalse(app.running);
        } finally {
            Entities.destroyAll(app.getManagementContext());
        }
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
    public void testLaunchCommandParsesArgs() throws ParseException {
        Cli<BrooklynCommand> cli = Main.buildCli();
        BrooklynCommand command = cli.parse("launch", "--app", "my.App", "--location", "localhost");
        assertTrue(command instanceof LaunchCommand, ""+command);
        String details = command.toString();
        assertTrue(details.contains("app=my.App"), details);   
        assertTrue(details.contains("script=null"), details);
        assertTrue(details.contains("location=localhost"), details);
        assertTrue(details.contains("port=8081"), details);
        assertTrue(details.contains("noConsole=false"), details);
        assertTrue(details.contains("noShutdownOnExit=false"), details);
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

    @Test
    public void testLaunchWillStartAppWhenGivenImpl() throws Exception {
        exampleAppConstructed = false;
        exampleAppRunning = false;
        
        Cli<BrooklynCommand> cli = Main.buildCli();
        final BrooklynCommand command = cli.parse("launch", "--noConsole", "--app", ExampleApp.class.getName(), "--location", "localhost");
        
        executor.submit(new Callable<Void>() {
            public Void call() throws Exception {
                try {
                    LOG.info("Calling command: "+command);
                    command.call();
                    return null;
                } catch (Throwable t) {
                    LOG.error("Error executing command", t);
                    throw Exceptions.propagate(t);
                }
            }});
        
        TestUtils.executeUntilSucceeds(new Runnable() {
            public void run() {
                assertTrue(exampleAppConstructed);
                assertTrue(exampleAppRunning);
            }
        });
    }

    // An empty app to be used for testing
    private static volatile boolean exampleAppRunning;
    private static volatile boolean exampleAppConstructed;
    
    public static class ExampleApp extends AbstractApplication {
        volatile boolean running;
        volatile boolean constructed;
        
        @Override public void init() {
            super.init();
            constructed = true;
            exampleAppConstructed = true;
        }
        @Override public void start(Collection<? extends Location> locations) {
            super.start(locations);
            running = true;
            exampleAppRunning = true;
        }
        @Override public void stop() {
            super.stop();
            running = false;
            exampleAppRunning = false;
        }
    }
    
    // An empty entity to be used for testing
    @ImplementedBy(ExampleEntityImpl.class)
    public static interface ExampleEntity extends Entity, Startable {
    }

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
