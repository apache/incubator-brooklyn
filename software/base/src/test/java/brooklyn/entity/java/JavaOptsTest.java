package brooklyn.entity.java;

import static org.testng.Assert.fail;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.location.PortRange;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.MutableList;
import brooklyn.util.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public class JavaOptsTest {

    // TODO Test setting classpath; but this works by customize() copying all the artifacts into /lib/*
    // so that we can simply set this on the classpath...
    
    private static final long TIMEOUT_MS = 10*1000;
    
    private static class ExecCmd {
        Map<String,?> props;
        String summaryForLogging;
        List<String> commands;
        Map<?,?> env;
        
        ExecCmd(Map<String,?> props, String summaryForLogging, List<String> commands, Map env) {
            this.props = props;
            this.summaryForLogging = summaryForLogging;
            this.commands = commands;
            this.env = env;
        }
    }
    
    private AbstractApplication app;
    private SshMachineLocation loc;
    List<ExecCmd> execScriptCmds;
    
    @BeforeMethod
    public void setUp() throws Exception {
        execScriptCmds = new CopyOnWriteArrayList<ExecCmd>();
        app = new TestApplication();
        loc = Mockito.mock(SshMachineLocation.class);
        Mockito.when(loc.getAddress()).thenReturn(InetAddress.getByName("localhost"));
        Mockito.when(loc.obtainPort(Mockito.<PortRange>anyObject())).thenReturn(1);
        Mockito.when(loc.execScript(Mockito.<Map>anyObject(), Mockito.anyString(), Mockito.<List<String>>anyObject(), Mockito.<Map>anyObject())).thenAnswer(
                new Answer<Integer>() {
                    @Override public Integer answer(InvocationOnMock invocation) throws Throwable {
                        Object[] args = invocation.getArguments();
                        execScriptCmds.add(new ExecCmd((Map)args[0], (String)args[1], (List<String>)args[2], (Map)args[3]));
                        return 0;
                    }
                });
    }
    
    @AfterMethod
    public void tearDown() {
        if (app != null) app.stop();
    }
    
    @Test
    public void testSimpleLaunchesJavaProcess() {
        VanillaJavaApp javaProcess = new VanillaJavaApp(MutableMap.builder()
                .put("main", "my.Main")
                .put("useJmx", false)
                .build(), 
                app);
        app.start(ImmutableList.of(loc));
        
        String runDir = javaProcess.getRunDir();
        Map<String,String> expectedEnvs = ImmutableMap.<String,String>of();
        List<String> expectedCmds = ImmutableList.of(String.format("java $JAVA_OPTS -cp \"lib/*\" my.Main  >> %s/console 2>&1 </dev/null &", runDir));
        
        assertHasExpectedCmds(expectedCmds, expectedEnvs);
    }
    
    @Test
    public void testPassesJavaArgs() {
        VanillaJavaApp javaProcess = new VanillaJavaApp(MutableMap.builder()
                .put("main", "my.Main")
                .put("args", ImmutableList.of("a1", "a2"))
                .put("useJmx", false)
                .build(), 
                app);
        app.start(ImmutableList.of(loc));
        
        String runDir = javaProcess.getRunDir();
        Map<String,String> expectedEnvs = ImmutableMap.<String,String>of();
        List<String> expectedCmds = ImmutableList.of(String.format("java $JAVA_OPTS -cp \"lib/*\" my.Main \"a1\" \"a2\" >> %s/console 2>&1 </dev/null &", runDir));
        
        assertHasExpectedCmds(expectedCmds, expectedEnvs);
    }
    
    @Test
    public void testPassesJavaOpts() {
        VanillaJavaApp javaProcess = new VanillaJavaApp(MutableMap.builder()
                .put("main", "my.Main")
                .put("javaOpts", ImmutableList.of("-abc"))
                .put("useJmx", false)
                .build(), 
                app);
        app.start(ImmutableList.of(loc));
        
        String runDir = javaProcess.getRunDir();
        String defaultJavaOpts = "-Xms128m -Xmx512m -XX:MaxPermSize=512m";
        String expectedJavaOpts = defaultJavaOpts+" -abc";
        Map<String,String> expectedEnvs = ImmutableMap.<String,String>of("JAVA_OPTS", expectedJavaOpts);
        List<String> expectedCmds = ImmutableList.of(String.format("java $JAVA_OPTS -cp \"lib/*\" my.Main  >> %s/console 2>&1 </dev/null &", runDir));
        
        assertHasExpectedCmds(expectedCmds, expectedEnvs);
    }
    
    @Test
    public void testPassesJavaSysProps() {
        VanillaJavaApp javaProcess = new VanillaJavaApp(MutableMap.builder()
                .put("main", "my.Main")
                .put("javaSysProps", ImmutableMap.of("mykey", "myval"))
                .put("useJmx", false)
                .build(), 
                app);
        app.start(ImmutableList.of(loc));
        
        String runDir = javaProcess.getRunDir();
        String defaultJavaOpts = "-Xms128m -Xmx512m -XX:MaxPermSize=512m";
        String expectedJavaOpts = defaultJavaOpts+" -Dmykey=myval";
        Map<String,String> expectedEnvs = ImmutableMap.<String,String>of("JAVA_OPTS", expectedJavaOpts);
        List<String> expectedCmds = ImmutableList.of(String.format("java $JAVA_OPTS -cp \"lib/*\" my.Main  >> %s/console 2>&1 </dev/null &", runDir));
        
        assertHasExpectedCmds(expectedCmds, expectedEnvs);
    }
    
    @Test
    public void testPassesJavaOptsOverridingDefaults() {
        VanillaJavaApp javaProcess = new VanillaJavaApp(MutableMap.builder()
                .put("main", "my.Main")
                .put("javaOpts", ImmutableList.of("-Xmx567m", "-XX:MaxPermSize=567m"))
                .put("useJmx", false)
                .build(), 
                app);
        app.start(ImmutableList.of(loc));
        
        String runDir = javaProcess.getRunDir();
        String expectedJavaOpts = "-Xms128m -Xmx567m -XX:MaxPermSize=567m";
        Map<String,String> expectedEnvs = ImmutableMap.<String,String>of("JAVA_OPTS", expectedJavaOpts);
        List<String> expectedCmds = ImmutableList.of(String.format("java $JAVA_OPTS -cp \"lib/*\" my.Main  >> %s/console 2>&1 </dev/null &", runDir));
        
        assertHasExpectedCmds(expectedCmds, expectedEnvs);
    }
    
    @Test
    public void testPassesJavaOptsObeyingMutualExclusions() {
        VanillaJavaApp javaProcess = new VanillaJavaApp(MutableMap.builder()
                .put("main", "my.Main")
                .put("javaOpts", ImmutableList.of("-client"))
                .put("useJmx", false)
                .build(), 
                app) {
            @Override public VanillaJavaAppSshDriver newDriver(SshMachineLocation loc) {
                return new VanillaJavaAppSshDriver(this, loc) {
                    @Override protected List<String> getCustomJavaConfigOptions() {
                        return MutableList.<String>builder()
                                .addAll(super.getCustomJavaConfigOptions())
                                .add("-server")
                                .build();
                    };
                };
            }
        };
        app.start(ImmutableList.of(loc));
        
        String runDir = javaProcess.getRunDir();
        String defaultJavaOpts = "-Xms128m -Xmx512m -XX:MaxPermSize=512m";
        String expectedJavaOpts = defaultJavaOpts+" -client";
        Map<String,String> expectedEnvs = ImmutableMap.<String,String>of("JAVA_OPTS", expectedJavaOpts);
        List<String> expectedCmds = ImmutableList.of(String.format("java $JAVA_OPTS -cp \"lib/*\" my.Main  >> %s/console 2>&1 </dev/null &", runDir));
        
        assertHasExpectedCmds(expectedCmds, expectedEnvs);
    }
    
    /**
     * Asserts that one of the scripts executed had these commands, and these environment variables.
     * There could be other commands in between though.
     */
    private void assertHasExpectedCmds(List<String> expectedCmds, Map<String,?> expectedEnvs) {
        for (ExecCmd cmd : execScriptCmds) {
            // TODO Check expectedCmds in-order
            if (cmd.commands.containsAll(expectedCmds) && Maps.<Object,Object>difference(cmd.env, expectedEnvs).entriesDiffering().isEmpty()) {
                return;
            }
        }
        
        for (ExecCmd cmd : execScriptCmds) {
            System.out.println("Command:");
            System.out.println("\tEnv:");
            for (Map.Entry<?,?> entry : cmd.env.entrySet()) {
                System.out.println("\t\t"+entry.getKey()+" = "+entry.getValue());
            }
            System.out.println("\tCmds:");
            for (String c : cmd.commands) {
                System.out.println("\t\t"+c);
            }
        }
        
        fail("Cmd not present: expected="+expectedCmds+"; actual="+execScriptCmds);
    }
}
