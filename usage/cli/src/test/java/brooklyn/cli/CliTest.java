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
package brooklyn.cli;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import groovy.lang.GroovyClassLoader;
import io.airlift.command.Cli;
import io.airlift.command.ParseException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.cli.Main.BrooklynCommand;
import brooklyn.cli.Main.GeneratePasswordCommand;
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
import brooklyn.test.Asserts;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.FatalConfigurationRuntimeException;
import brooklyn.util.exceptions.UserFacingException;
import brooklyn.util.time.Duration;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class CliTest {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractEntity.class);

    // See testInvokeGroovyScript test for usage
    public static final AtomicBoolean GROOVY_INVOKED = new AtomicBoolean(false);

    private ExecutorService executor;
    private StartableApplication app;
    private static volatile ExampleEntity exampleEntity;

    // static so that they can be set from the static classes ExampleApp and ExampleEntity
    private static volatile boolean exampleAppRunning;
    private static volatile boolean exampleAppConstructed;
    private static volatile boolean exampleEntityRunning;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        executor = Executors.newCachedThreadPool();
        exampleAppConstructed = false;
        exampleAppRunning = false;
        exampleEntityRunning = false;
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (executor != null) executor.shutdownNow();
        if (app != null) Entities.destroyAll(app.getManagementContext());
        if (exampleEntity != null && exampleEntity.getApplication() != null) Entities.destroyAll(exampleEntity.getApplication().getManagementContext());
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
        assertTrue(Iterables.getOnlyElement(entities) instanceof ExampleEntity, "entities="+entities+"; ifs="+Iterables.getOnlyElement(entities).getClass().getInterfaces());
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
    public void testLoadApplicationByParsingGroovyFile() throws Exception {
        String appName = "ExampleAppInFile.groovy"; // file found in src/test/resources (contains empty app)
        Object appBuilder = loadApplicationFromClasspathOrParse(appName);
        assertTrue(appBuilder instanceof ApplicationBuilder, "app="+appBuilder);
        assertAppWrappedInBuilder((ApplicationBuilder)appBuilder, "ExampleAppInFile");
    }
    
    private Object loadApplicationFromClasspathOrParse(String appName) throws Exception {
        LaunchCommand launchCommand = new Main.LaunchCommand();
        ResourceUtils resourceUtils = ResourceUtils.create(this);
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
    
    @Test
    public void testInvokeGroovyScript() throws Exception {
        File groovyFile = File.createTempFile("testinvokegroovy", "groovy");
        try {
            String contents = CliTest.class.getCanonicalName()+".GROOVY_INVOKED.set(true);";
            Files.write(contents.getBytes(), groovyFile);

            LaunchCommand launchCommand = new Main.LaunchCommand();
            ResourceUtils resourceUtils = ResourceUtils.create(this);
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

    protected Cli<BrooklynCommand> buildCli() {
        return new Main().cliBuilder().build();
    }
    
    @Test
    public void testLaunchCommandParsesArgs() throws ParseException {
        BrooklynCommand command = buildCli().parse("launch", 
                "--app", "my.App", 
                "--location", "localhost",
                "--port", "1234",
                "--bindAddress", "myhostname",
                "--noConsole", "--noConsoleSecurity", "--noShutdownOnExit", "--stopOnKeyPress", 
                "--localBrooklynProperties", "/path/to/myprops",
                LaunchCommand.PERSIST_OPTION, LaunchCommand.PERSIST_OPTION_REBIND, 
                "--persistenceDir", "/path/to/mypersist",
                LaunchCommand.HA_OPTION, LaunchCommand.HA_OPTION_STANDBY);
        assertTrue(command instanceof LaunchCommand, ""+command);
        String details = command.toString();
        assertTrue(details.contains("app=my.App"), details);   
        assertTrue(details.contains("script=null"), details);
        assertTrue(details.contains("location=localhost"), details);
        assertTrue(details.contains("port=1234"), details);
        assertTrue(details.contains("bindAddress=myhostname"), details);
        assertTrue(details.contains("noConsole=true"), details);
        assertTrue(details.contains("noConsoleSecurity=true"), details);
        assertTrue(details.contains("noShutdownOnExit=true"), details);
        assertTrue(details.contains("stopOnKeyPress=true"), details);
        assertTrue(details.contains("localBrooklynProperties=/path/to/myprops"), details);
        assertTrue(details.contains("persist=rebind"), details);
        assertTrue(details.contains("persistenceDir=/path/to/mypersist"), details);
        assertTrue(details.contains("highAvailability=standby"), details);
    }

    @Test
    public void testLaunchCommandUsesDefaults() throws ParseException {
        BrooklynCommand command = buildCli().parse("launch");
        assertTrue(command instanceof LaunchCommand, ""+command);
        String details = command.toString();
        assertTrue(details.contains("app=null"), details);   
        assertTrue(details.contains("script=null"), details);
        assertTrue(details.contains("location=null"), details);
        assertTrue(details.contains("port=8081"), details);
        assertTrue(details.contains("noConsole=false"), details);
        assertTrue(details.contains("noConsoleSecurity=false"), details);
        assertTrue(details.contains("noShutdownOnExit=false"), details);
        assertTrue(details.contains("stopOnKeyPress=false"), details);
        assertTrue(details.contains("localBrooklynProperties=null"), details);
        assertTrue(details.contains("persist=disabled"), details);
        assertTrue(details.contains("persistenceDir=null"), details);
        assertTrue(details.contains("highAvailability=auto"), details);
    }

    @Test
    public void testLaunchCommandComplainsWithInvalidArgs() {
        Cli<BrooklynCommand> cli = buildCli();
        try {
            BrooklynCommand command = cli.parse("launch", "invalid");
            command.call();
            Assert.fail("Should have thrown exception; instead got "+command);
        } catch (ParseException e) {
            /* expected */
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    @Test
    public void testAppOptionIsOptional() throws ParseException {
        Cli<BrooklynCommand> cli = buildCli();
        cli.parse("launch", "blah", "my.App");
    }
    
    public void testHelpCommand() {
        Cli<BrooklynCommand> cli = buildCli();
        BrooklynCommand command = cli.parse("help");
        assertTrue(command instanceof HelpCommand);
        command = cli.parse();
        assertTrue(command instanceof HelpCommand);
    }

    @Test
    public void testLaunchWillStartAppWhenGivenImpl() throws Exception {
        Cli<BrooklynCommand> cli = buildCli();
        BrooklynCommand command = cli.parse("launch", "--noConsole", "--app", ExampleApp.class.getName(), "--location", "localhost");
        submitCommandAndAssertRunnableSucceeds(command, new Runnable() {
                public void run() {
                    assertTrue(exampleAppConstructed);
                    assertTrue(exampleAppRunning);
                }
            });
    }

    @Test
    public void testLaunchStartsYamlApp() throws Exception {
        Cli<BrooklynCommand> cli = buildCli();
        BrooklynCommand command = cli.parse("launch", "--noConsole", "--app", "example-app-no-location.yaml", "--location", "localhost");
        submitCommandAndAssertRunnableSucceeds(command, new Runnable() {
                public void run() {
                    assertTrue(exampleEntityRunning);
                }
            });
    }
    
    @Test
    public void testLaunchStartsYamlAppWithCommandLineLocation() throws Exception {
        Cli<BrooklynCommand> cli = buildCli();
        BrooklynCommand command = cli.parse("launch", "--noConsole", "--app", "example-app-no-location.yaml", "--location", "localhost:(name=testLocalhost)");
        submitCommandAndAssertRunnableSucceeds(command, new Runnable() {
                public void run() {
                    assertTrue(exampleEntityRunning);
                    assertTrue(Iterables.getOnlyElement(exampleEntity.getApplication().getLocations()).getDisplayName().equals("testLocalhost"));
                }
            });
    }
    
    @Test
    public void testLaunchStartsYamlAppWithYamlAppLocation() throws Exception {
        Cli<BrooklynCommand> cli = buildCli();
        BrooklynCommand command = cli.parse("launch", "--noConsole", "--app", "example-app-app-location.yaml");
        submitCommandAndAssertRunnableSucceeds(command, new Runnable() {
                public void run() {
                    assertTrue(exampleEntityRunning);
                    assertTrue(Iterables.getOnlyElement(exampleEntity.getApplication().getLocations()).getDisplayName().equals("appLocalhost"));
                }
            });
    }
    
    @Test
    public void testLaunchStartsYamlAppWithYamlAndAppCliLocation() throws Exception {
        Cli<BrooklynCommand> cli = buildCli();
        BrooklynCommand command = cli.parse("launch", "--noConsole", "--app", "example-app-app-location.yaml", "--location", "localhost");
        submitCommandAndAssertRunnableSucceeds(command, new Runnable() {
                public void run() {
                    assertTrue(exampleEntityRunning);
                    assertTrue(Iterables.getFirst(exampleEntity.getApplication().getLocations(), null).getDisplayName().equals("appLocalhost"));
                }
            });
    }

    @Test
    public void testGeneratePasswordCommandParsed() throws Exception {
        Cli<BrooklynCommand> cli = buildCli();
        BrooklynCommand command = cli.parse("generate-password", "--user", "myname");
        
        assertTrue(command instanceof GeneratePasswordCommand);
    }

    @Test
    public void testGeneratePasswordFromStdin() throws Exception {
        List<String> stdoutLines = runCommand(ImmutableList.of("generate-password", "--user", "myname", "--stdin"), "mypassword\nmypassword\n");
        
        System.out.println(stdoutLines);
    }

    @Test
    public void testGeneratePasswordFailsIfPasswordsDontMatch() throws Throwable {
        Throwable exception = runCommandExpectingException(ImmutableList.of("generate-password", "--user", "myname", "--stdin"), "mypassword\ndifferentpassword\n");
        if (exception instanceof UserFacingException && exception.toString().contains("Passwords did not match")) {
            // success
        } else {
            throw new Exception(exception);
        }
    }

    @Test
    public void testGeneratePasswordFailsIfNoConsole() throws Throwable {
        Throwable exception = runCommandExpectingException(ImmutableList.of("generate-password", "--user", "myname"), "");
        if (exception instanceof FatalConfigurationRuntimeException && exception.toString().contains("No console")) {
            // success
        } else {
            throw new Exception(exception);
        }
    }
    
    @Test
    public void testGeneratePasswordFailsIfPasswordBlank() throws Throwable {
        Throwable exception = runCommandExpectingException(ImmutableList.of("generate-password", "--user", "myname", "--stdin"), "\n\n");
        if (exception instanceof UserFacingException && exception.toString().contains("Password must not be blank")) {
            // success
        } else {
            throw new Exception(exception);
        }
    }

    protected Throwable runCommandExpectingException(Iterable<String> args, String input) throws Exception {
        try {
            List<String> stdout = runCommand(args, input);
            fail("Expected exception, but got stdout="+stdout);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        }
    }

    protected List<String> runCommand(Iterable<String> args, String input) throws Exception {
        Cli<BrooklynCommand> cli = buildCli();
        final BrooklynCommand command = cli.parse(args);
        
        final AtomicReference<Exception> exception = new AtomicReference<Exception>();
        Thread t= new Thread(new Runnable() {
            public void run() {
                try {
                    command.call();
                } catch (Exception e) {
                    exception.set(e);
                    throw Exceptions.propagate(e);
                }
            }});
        
        InputStream origIn = System.in;
        PrintStream origOut = System.out;
        try {
            InputStream stdin = new ByteArrayInputStream(input.getBytes());
            System.setIn(stdin);

            ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
            PrintStream stdout = new PrintStream(stdoutBytes);
            System.setOut(stdout);

            t.start();

            t.join(10*1000);
            assertFalse(t.isAlive());
            
            if (exception.get() != null) {
                throw new ExecutionException(exception.get());
            }
            
            return ImmutableList.copyOf(Splitter.on("\n").split(new String(stdoutBytes.toByteArray())));
        } finally {
            System.setIn(origIn);
            System.setOut(origOut);
            t.interrupt();
        }
    }

    private void submitCommandAndAssertRunnableSucceeds(final BrooklynCommand command, Runnable runnable) {
        if (command instanceof LaunchCommand) {
            ((LaunchCommand)command).useManagementContext(new LocalManagementContextForTests());
        }
        executor.submit(new Callable<Void>() {
            public Void call() throws Exception {
                try {
                    LOG.info("Calling command: "+command);
                    command.call();
                    return null;
                } catch (Throwable t) {
                    LOG.error("Error executing command: "+t, t);
                    throw Exceptions.propagate(t);
                }
            }});

        Asserts.succeedsEventually(MutableMap.of("timeout", Duration.ONE_MINUTE), runnable);
    }

    //  An empty app to be used for testing
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
        public ExampleEntityImpl() {
            super();
            exampleEntity = this;
        }
        @Override public void start(Collection<? extends Location> locations) {
            exampleEntityRunning = true;
        }
        @Override public void stop() {
            exampleEntityRunning = false;
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
