package brooklyn.entity.java;

import static org.testng.Assert.fail;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.LocationSpec;
import brooklyn.location.MachineLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.jmx.jmxmp.JmxmpAgent;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class JavaOptsTest extends BrooklynAppUnitTestSupport {

    // TODO Test setting classpath; but this works by customize() copying all the artifacts into /lib/*
    // so that we can simply set this on the classpath...
    
    private static final Logger log = LoggerFactory.getLogger(JavaOptsTest.class);
    
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
        
        @Override
        public String toString() {
            return "ExecCmd["+summaryForLogging+": "+commands+"; "+props+"; "+env+"]";
        }
    }
    
    public static class RecordingSshTool implements SshTool {
        static List<ExecCmd> execScriptCmds = Lists.newCopyOnWriteArrayList();
        
        private boolean connected;
        
        public RecordingSshTool(Map<?,?> props) {
        }
        @Override public void connect() {
            connected = true;
        }
        @Override public void connect(int maxAttempts) {
            connected = true;
        }
        @Override public void disconnect() {
            connected = false;
        }
        @Override public boolean isConnected() {
            return connected;
        }
        @Override public int execScript(Map<String, ?> props, List<String> commands, Map<String, ?> env) {
            execScriptCmds.add(new ExecCmd(props, "", commands, env));
            return 0;
        }
        @Override public int execScript(Map<String, ?> props, List<String> commands) {
            return execScript(props, commands, ImmutableMap.<String,Object>of());
        }
        @Override public int execCommands(Map<String, ?> props, List<String> commands, Map<String, ?> env) {
            execScriptCmds.add(new ExecCmd(props, "", commands, env));
            return 0;
        }
        @Override public int execCommands(Map<String, ?> props, List<String> commands) {
            return execCommands(props, commands, ImmutableMap.<String,Object>of());
        }
        @Override public int copyToServer(Map<String, ?> props, File localFile, String pathAndFileOnRemoteServer) {
            return 0;
        }
        @Override public int copyToServer(Map<String, ?> props, InputStream contents, String pathAndFileOnRemoteServer) {
            return 0;
        }
        @Override public int copyToServer(Map<String, ?> props, byte[] contents, String pathAndFileOnRemoteServer) {
            return 0;
        }
        @Override public int copyFromServer(Map<String, ?> props, String pathAndFileOnRemoteServer, File local) {
            return 0;
        }
    }

    private SshMachineLocation loc;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        RecordingSshTool.execScriptCmds.clear();
        super.setUp();
        loc = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", "localhost")
                .configure(SshTool.PROP_TOOL_CLASS, RecordingSshTool.class.getName()));
    }
    
    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        RecordingSshTool.execScriptCmds.clear();
    }
    
    @Test
    public void testSimpleLaunchesJavaProcess() {
        VanillaJavaApp javaProcess = app.createAndManageChild(EntitySpec.create(VanillaJavaApp.class)
            .configure("main", "my.Main").configure("useJmx", false));
        app.start(ImmutableList.of(loc));
        
        String runDir = javaProcess.getRunDir();
        Map<String,String> expectedEnvs = ImmutableMap.<String,String>of();
        List<String> expectedCmds = ImmutableList.of(String.format("java $JAVA_OPTS -cp \"%1$s/lib\" my.Main  >> %1$s/console 2>&1 </dev/null &", runDir));
        
        assertHasExpectedCmds(expectedCmds, expectedEnvs);
    }
    
    @Test
    public void testPassesJavaArgs() {
        VanillaJavaApp javaProcess = app.createAndManageChild(EntitySpec.create(VanillaJavaApp.class)
            .configure("main", "my.Main").configure("useJmx", false).configure("args", ImmutableList.of("a1", "a2")));
        app.start(ImmutableList.of(loc));
        
        String runDir = javaProcess.getRunDir();
        Map<String,String> expectedEnvs = ImmutableMap.<String,String>of();
        List<String> expectedCmds = ImmutableList.of(String.format("java $JAVA_OPTS -cp \"%1$s/lib\" my.Main \"a1\" \"a2\" >> %1$s/console 2>&1 </dev/null &", runDir));
        
        assertHasExpectedCmds(expectedCmds, expectedEnvs);
    }
    
    @Test
    public void testPassesJavaOpts() {
        VanillaJavaApp javaProcess = app.createAndManageChild(EntitySpec.create(VanillaJavaApp.class)
            .configure("main", "my.Main").configure("useJmx", false).configure("javaOpts", ImmutableList.of("-abc")));
        app.start(ImmutableList.of(loc));
        
        String runDir = javaProcess.getRunDir();
        String defaultJavaOpts = "-Xms128m -Xmx512m -XX:MaxPermSize=512m";
        String expectedJavaOpts = defaultJavaOpts+" -abc";
        Map<String,String> expectedEnvs = ImmutableMap.<String,String>of("JAVA_OPTS", expectedJavaOpts);
        List<String> expectedCmds = ImmutableList.of(String.format("java $JAVA_OPTS -cp \"%1$s/lib\" my.Main  >> %1$s/console 2>&1 </dev/null &", runDir));
        
        assertHasExpectedCmds(expectedCmds, expectedEnvs);
    }

    @Test
    public void testPassesJavaSysProps() {
        VanillaJavaApp javaProcess = app.createAndManageChild(EntitySpec.create(VanillaJavaApp.class)
            .configure("main", "my.Main").configure("useJmx", false).configure("javaSysProps", ImmutableMap.of("mykey", "myval")));

        app.start(ImmutableList.of(loc));
        
        String runDir = javaProcess.getRunDir();
        String defaultJavaOpts = "-Xms128m -Xmx512m -XX:MaxPermSize=512m";
        String expectedJavaOpts = defaultJavaOpts+" -Dmykey=myval";
        Map<String,String> expectedEnvs = ImmutableMap.<String,String>of("JAVA_OPTS", expectedJavaOpts);
        List<String> expectedCmds = ImmutableList.of(String.format("java $JAVA_OPTS -cp \"%1$s/lib\" my.Main  >> %1$s/console 2>&1 </dev/null &", runDir));
        
        assertHasExpectedCmds(expectedCmds, expectedEnvs);
    }
    
    @Test
    public void testPassesJavaOptsOverridingDefaults() {
        VanillaJavaApp javaProcess = app.createAndManageChild(EntitySpec.create(VanillaJavaApp.class)
            .configure("main", "my.Main").configure("useJmx", false).configure("javaOpts", ImmutableList.of("-Xmx567m", "-XX:MaxPermSize=567m")));
        app.start(ImmutableList.of(loc));
        
        String runDir = javaProcess.getRunDir();
        Object expectedJavaOpts = MutableSet.of("-Xms128m", "-Xmx567m", "-XX:MaxPermSize=567m");
        Map<String,Object> expectedEnvs = ImmutableMap.<String,Object>of("JAVA_OPTS", expectedJavaOpts);
        List<String> expectedCmds = ImmutableList.of(String.format("java $JAVA_OPTS -cp \"%1$s/lib\" my.Main  >> %1$s/console 2>&1 </dev/null &", runDir));

        assertHasExpectedCmds(expectedCmds, expectedEnvs);
    }
    
    public static class TestingJavaOptsVanillaJavaAppImpl extends VanillaJavaAppImpl {
        @Override public VanillaJavaAppSshDriver newDriver(MachineLocation loc) {
            return new VanillaJavaAppSshDriver(this, (SshMachineLocation)loc) {
                @Override protected List<String> getCustomJavaConfigOptions() {
                    return MutableList.<String>builder()
                        .addAll(super.getCustomJavaConfigOptions())
                        .add("-server")
                        .build();
                };
            };
        }
    }
    
    @Test
    public void testPassesJavaOptsObeyingMutualExclusions() {
        VanillaJavaApp javaProcess = app.createAndManageChild(EntitySpec.create(VanillaJavaApp.class, TestingJavaOptsVanillaJavaAppImpl.class)
            .configure("main", "my.Main").configure("useJmx", false).configure("javaOpts", ImmutableList.of("-client")));
        app.start(ImmutableList.of(loc));
        
        String runDir = javaProcess.getRunDir();
        String defaultJavaOpts = "-Xms128m -Xmx512m -XX:MaxPermSize=512m";
        String expectedJavaOpts = defaultJavaOpts+" -client";
        Map<String,String> expectedEnvs = ImmutableMap.<String,String>of("JAVA_OPTS", expectedJavaOpts);
        List<String> expectedCmds = ImmutableList.of(String.format("java $JAVA_OPTS -cp \"%1$s/lib\" my.Main  >> %1$s/console 2>&1 </dev/null &", runDir));
        
        assertHasExpectedCmds(expectedCmds, expectedEnvs);
    }
    
    /**
     * Asserts that one of the scripts executed had these commands, and these environment variables.
     * There could be other commands in between though.
     */
    private void assertHasExpectedCmds(List<String> expectedCmds, Map<String,?> expectedEnvs) {
        if (RecordingSshTool.execScriptCmds.isEmpty())
            fail("No commands recorded");
        
        for (ExecCmd cmd : RecordingSshTool.execScriptCmds) {
            // TODO Check expectedCmds in-order
            
            // check if expectedEnv is a set, then the string value contains all elements in the set
            Map<Object, ValueDifference<Object>> difference = Maps.<Object,Object>difference(cmd.env, expectedEnvs).entriesDiffering();
            boolean same = difference.isEmpty();
            if (!same) {
                Set<Object> differingKeys = new LinkedHashSet<Object>(difference.keySet());
                Iterator<Object> ki = differingKeys.iterator();
                while (ki.hasNext()) {
                    Object key = ki.next();
                    Object expectationHere = expectedEnvs.get(key);
                    Object valueHere = cmd.env.get(key);
                    if (valueHere==null) break;
                    
                    if (expectationHere instanceof Set) {
                        Set mutableExpectationHere = new LinkedHashSet(((Set)expectationHere));
                        Iterator si = ((Set)mutableExpectationHere).iterator();
                        while (si.hasNext()) {
                            Object oneExpectationHere = si.next();
                            if (valueHere.toString().contains(Strings.toString(oneExpectationHere)))
                                si.remove();
                            else break;
                        }
                        if (mutableExpectationHere.isEmpty())
                            differingKeys.remove(key);
                        else
                            // not the same
                            break;
                    } else {
                        // not the same
                        break;
                    }
                }
                if (differingKeys.isEmpty())
                    same = true;
            }
            
            if (cmd.commands.containsAll(expectedCmds) && same) {
                return;
            }
        }
        
        for (ExecCmd cmd : RecordingSshTool.execScriptCmds) {
            log.info("Command:");
            log.info("\tEnv:");
            for (Map.Entry<?,?> entry : cmd.env.entrySet()) {
                log.info("\t\t"+entry.getKey()+" = "+entry.getValue());
            }
            log.info("\tCmds:");
            for (String c : cmd.commands) {
                log.info("\t\t"+c);
            }
        }
        
        fail("Cmd not present: expected="+expectedCmds+"/"+expectedEnvs+"; actual="+RecordingSshTool.execScriptCmds);
    }

    public static class TestingNoSensorsVanillaJavaAppImpl extends VanillaJavaAppImpl {
        protected void connectSensors() {
            /* nothing here */
            setAttribute(SERVICE_UP, true);
        }
    }
    
    private void assertJmxWithPropsHasPhrases(Map props,
            List<String> expectedPhrases,
            List<String> forbiddenPhrases) {
        if (!props.containsKey("main")) props.put("main", "my.Main");
        @SuppressWarnings({ "unused" })
        VanillaJavaApp javaProcess = app.createAndManageChild(EntitySpec.create(VanillaJavaApp.class, TestingNoSensorsVanillaJavaAppImpl.class)
            .configure(props));
        app.start(ImmutableList.of(loc));
        
        List<String> phrases = new ArrayList<String>(expectedPhrases);
        Set<String> forbiddenPhrasesFound = new LinkedHashSet<String>();
        for (ExecCmd cmd : RecordingSshTool.execScriptCmds) {
            String biggun = ""+cmd.env+" "+cmd.commands;
            Iterator<String> pi = phrases.iterator();
            while (pi.hasNext()) {
                String phrase = pi.next();
                if (biggun.contains(phrase)) pi.remove();
            }
            if (forbiddenPhrases!=null)
                for (String p: forbiddenPhrases)
                    if (biggun.contains(p)) forbiddenPhrasesFound.add(p);
        }
        
        if (!phrases.isEmpty()) {
            log.warn("Missing phrases in commands: "+phrases+"\nCOMMANDS: "+RecordingSshTool.execScriptCmds);
            fail("Missing phrases in commands: "+phrases);
        }
        if (!forbiddenPhrasesFound.isEmpty()) {
            log.warn("Forbidden phrases found in commands: "+forbiddenPhrasesFound+"\nCOMMANDS: "+RecordingSshTool.execScriptCmds);
            fail("Forbidden phrases found in commands: "+forbiddenPhrasesFound);
        }
    }
    
    private static final List<String> EXPECTED_BASIC_JMX_OPTS = Arrays.asList(
            "-Dcom.sun.management.jmxremote",
            "-Dcom.sun.management.jmxremote.ssl=false",
            "-Dcom.sun.management.jmxremote.authenticate=false"
        );

    private static final List<String> FORBIDDEN_BASIC_JMX_OPTS = Arrays.asList(
            "-Dcom.sun.management.jmxremote.ssl=true",
            
            // often breaks things, as this is an advertised hostname usually;
            // it typically listens on all interfaces anyway
            "-Djava.rmi.server.hostname=0.0.0.0"
        );

    @Test
    public void testBasicJmxFromFlag() {
        assertJmxWithPropsHasPhrases(
                MutableMap.builder().
                put("useJmx", true).
                build(), 
            EXPECTED_BASIC_JMX_OPTS,
            FORBIDDEN_BASIC_JMX_OPTS);
    }

    @Test
    public void testBasicJmxFromConfig() {
        assertJmxWithPropsHasPhrases(
                MutableMap.builder().
                put(UsesJmx.USE_JMX, true).
                build(), 
            EXPECTED_BASIC_JMX_OPTS,
            FORBIDDEN_BASIC_JMX_OPTS);
    }

    @Test
    public void testBasicJmxConfigFromDefault() {
        assertJmxWithPropsHasPhrases(
                MutableMap.builder().
                build(), 
            EXPECTED_BASIC_JMX_OPTS,
            FORBIDDEN_BASIC_JMX_OPTS);
    }
    
    @Test
    public void testSecureJmxConfigFromDefault() {
        final List<String> EXPECTED_SECURE_JMX_OPTS = Arrays.asList(
                "-Dcom.sun.management.jmxremote",
                "-Dbrooklyn.jmxmp.port=31009",
                "-Dcom.sun.management.jmxremote.ssl=true",
                "-D"+JmxmpAgent.AUTHENTICATE_CLIENTS_PROPERTY+"=true",
                "keyStore", "/jmx-keystore",
                "trustStore", "/jmx-truststore",
                "-javaagent", "brooklyn-jmxmp-agent"
            );

        final List<String> FORBIDDEN_SECURE_JMX_OPTS = Arrays.asList(
                "-Dcom.sun.management.jmxremote.authenticate=true",
                "-Dcom.sun.management.jmxremote.ssl=false",
                // hostname isn't forbidden -- but it is generally not used now
                "-Djava.rmi.server.hostname="
            );
        
        assertJmxWithPropsHasPhrases(
                MutableMap.builder()
                        .put(UsesJmx.JMX_SSL_ENABLED, true)
                        .put(UsesJmx.JMX_PORT, 31009)
                        .build(), 
                EXPECTED_SECURE_JMX_OPTS,
                FORBIDDEN_SECURE_JMX_OPTS);
    }
}
