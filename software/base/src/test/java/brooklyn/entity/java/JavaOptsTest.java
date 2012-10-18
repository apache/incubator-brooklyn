package brooklyn.entity.java;

import static org.testng.Assert.fail;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.location.PortRange;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.MutableList;
import brooklyn.util.MutableMap;
import brooklyn.util.jmx.jmxmp.JmxmpAgent;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

@SuppressWarnings({ "rawtypes", "serial", "unchecked" })
public class JavaOptsTest {

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

    /*
    java.lang.AssertionError: Cmd not present: expected=[
java $JAVA_OPTS -cp "lib/*" my.Main  >> /tmp/brooklyn-alex/apps/pdbzOGUQ/entities/
        VanillaJavaApp_DmoMEajV/console 2>&1 </dev/null &
java $JAVA_OPTS -cp "lib/*" my.Main  >> /tmp/brooklyn-alex/apps/pdbzOGUQ/entities/
        VanillaJavaApp_DmoMEajV/console 2>&1 </dev/null &
]; actual=[ExecCmd[

installing VanillaJavaApp[id=DmoMEajV,displayName=VanillaJavaApp:DmoM]: [
export INSTALL_DIR="/tmp/brooklyn-alex/installs/VanillaJavaApp", 
mkdir -p $INSTALL_DIR, cd $INSTALL_DIR, test -f BROOKLYN && exit 0, date > $INSTALL_DIR/BROOKLYN]; 
{out=, err=, logPrefix=DmoMEajV@null}; 
{JAVA_OPTS=-Dmykey=myval -Xms128m -Xmx512m -XX:MaxPermSize=512m}

], ExecCmd[customizing VanillaJavaApp[id=DmoMEajV,displayName=VanillaJavaApp:DmoM]: [
export RUN_DIR="/tmp/brooklyn-alex/apps/pdbzOGUQ/entities/VanillaJavaApp_DmoMEajV", 
mkdir -p $RUN_DIR, cd $RUN_DIR, 
mkdir -p /tmp/brooklyn-alex/apps/pdbzOGUQ/entities/VanillaJavaApp_DmoMEajV/lib]; 
{logPrefix=DmoMEajV@null}; 
{JAVA_OPTS=-Dmykey=myval -Xms128m -Xmx512m -XX:MaxPermSize=512m}], 

ExecCmd[launching VanillaJavaApp[id=DmoMEajV,displayName=VanillaJavaApp:DmoM]: [

export RUN_DIR="/tmp/brooklyn-alex/apps/pdbzOGUQ/entities/VanillaJavaApp_DmoMEajV", 
mkdir -p $RUN_DIR, cd $RUN_DIR, echo "launching: java $JAVA_OPTS -cp 'lib/*' my.Main ", 
java $JAVA_OPTS -cp "lib/*" my.Main  >> /tmp/brooklyn-alex/apps/pdbzOGUQ/entities/
        VanillaJavaApp_DmoMEajV/console 2>&1 </dev/null &

, 
 * echo $! > /tmp/brooklyn-alex/apps/pdbzOGUQ/entities/VanillaJavaApp_DmoMEajV/pid.txt]; {out=, err=, logPrefix=DmoMEajV@null}; {JAVA_OPTS=-Dmykey=myval -Xms128m -Xmx512m -XX:MaxPermSize=512m}], ExecCmd[check-running VanillaJavaApp[id=DmoMEajV,displayName=VanillaJavaApp:DmoM]: [export RUN_DIR="/tmp/brooklyn-alex/apps/pdbzOGUQ/entities/VanillaJavaApp_DmoMEajV", mkdir -p $RUN_DIR, cd $RUN_DIR, test -f /tmp/brooklyn-alex/apps/pdbzOGUQ/entities/VanillaJavaApp_DmoMEajV/pid.txt || exit 1, ps -p `cat /tmp/brooklyn-alex/apps/pdbzOGUQ/entities/VanillaJavaApp_DmoMEajV/pid.txt`]; {logPrefix=DmoMEajV@null}; {JAVA_OPTS=-Dmykey=myval -Xms128m -Xmx512m -XX:MaxPermSize=512m}]]
 */

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
        expectedCmds.get(0).equals( execScriptCmds.get(2).commands.get(4) );
        fail("Cmd not present: expected="+expectedCmds+"/"+expectedEnvs+"; actual="+execScriptCmds);
    }

    private void assertJmxWithPropsHasPhrases(Map props,
            List<String> expectedPhrases,
            List<String> forbiddenPhrases) {
        if (!props.containsKey("main")) props.put("main", "my.Main");
        @SuppressWarnings({ "unused" })
        VanillaJavaApp javaProcess = new VanillaJavaApp(props, app) {
            protected void connectSensors() {
                /* nothing here */
            }
        };
        app.start(ImmutableList.of(loc));
        
        List<String> phrases = new ArrayList<String>(expectedPhrases);
        Set<String> forbiddenPhrasesFound = new LinkedHashSet<String>();
        for (ExecCmd cmd : execScriptCmds) {
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
            log.warn("Missing phrases in commands: "+phrases+"\nCOMMANDS: "+execScriptCmds);
            fail("Missing phrases in commands: "+phrases);
        }
        if (!forbiddenPhrasesFound.isEmpty()) {
            log.warn("Forbidden phrases found in commands: "+forbiddenPhrasesFound+"\nCOMMANDS: "+execScriptCmds);
            fail("Forbidden phrases found in commands: "+forbiddenPhrasesFound);
        }
    }
    
    private static final List<String> EXPECTED_BASIC_JMX_OPTS = Arrays.asList(
            "-Dcom.sun.management.jmxremote",
            "-Dcom.sun.management.jmxremote.port=1",
            "-Dcom.sun.management.jmxremote.ssl=false",
            "-Dcom.sun.management.jmxremote.authenticate=false",
            "-Djava.rmi.server.hostname="
        );

    private static final List<String> FORBIDDEN_BASIC_JMX_OPTS = Arrays.asList(
            "-Dcom.sun.management.jmxremote.ssl=true",
            
            // not sure if this breaks things or not!
            // (but without server wildcards property set false it listens on all interfaces anyway)
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
    
    private static final List<String> EXPECTED_SECURE_JMX_OPTS = Arrays.asList(
            "-Dcom.sun.management.jmxremote",
            "-Dbrooklyn.jmxmp.port=1",
            "-Dcom.sun.management.jmxremote.ssl=true",
            "-D"+JmxmpAgent.AUTHENTICATE_CLIENTS_PROPERTY+"=true",
            "-Djava.rmi.server.hostname=",
            "keyStore", "/jmx-keystore",
            "trustStore", "/jmx-truststore",
            "-javaagent", "brooklyn-jmxmp-agent"
        );

    private static final List<String> FORBIDDEN_SECURE_JMX_OPTS = Arrays.asList(
            "-Dcom.sun.management.jmxremote.authenticate=true",
            "-Dcom.sun.management.jmxremote.ssl=false"
        );

    @Test
    public void testSecureJmxConfigFromDefault() {
        assertJmxWithPropsHasPhrases(
                MutableMap.builder().
                put(UsesJmx.JMX_SSL_ENABLED, true).
                build(), 
            EXPECTED_SECURE_JMX_OPTS,
            FORBIDDEN_SECURE_JMX_OPTS);
    }
/*

2012-10-18 22:47:40,875 WARN  Missing phrases in commands: [-Dcom.sun.management.jmxremote.port=1]
COMMANDS: [ExecCmd[JMX_SETUP_PREINSTALL brooklyn.entity.java.JavaOptsTest$3[id=CPEr9F4S,displayName=:CPEr]: [mkdir -p /tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S]; {logPrefix=CPEr9F4S@null}; {JAVA_OPTS=-javaagent:/tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/brooklyn-jmxmp-agent-shaded-0.5.0-SNAPSHOT.jar -Xms128m -Xmx512m -XX:MaxPermSize=512m 

-Dcom.sun.management.jmxremote -Djava.rmi.server.hostname=localhost -Dbrooklyn.jmxmp.port=1 -Dcom.sun.management.jmxremote.ssl=true -Dbrooklyn.jmxmp.ssl.authenticate=true -Dcom.sun.management.jmxremote.authenticate=false -Dbrooklyn.jmxmp.ssl.keyStore=/tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/jmx-keystore -Dbrooklyn.jmxmp.ssl.trustStore=/tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/jmx-truststore}], ExecCmd[installing brooklyn.entity.java.JavaOptsTest$3[id=CPEr9F4S,displayName=:CPEr]: [export INSTALL_DIR="/tmp/brooklyn-alex/installs/brooklyn.entity.java.JavaOptsTest$3", mkdir -p $INSTALL_DIR, cd $INSTALL_DIR, test -f BROOKLYN && exit 0, date > $INSTALL_DIR/BROOKLYN]; {out=, err=, logPrefix=CPEr9F4S@null}; {JAVA_OPTS=-javaagent:/tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/brooklyn-jmxmp-agent-shaded-0.5.0-SNAPSHOT.jar -Xms128m -Xmx512m -XX:MaxPermSize=512m -Dcom.sun.management.jmxremote -Djava.rmi.server.hostname=localhost -Dbrooklyn.jmxmp.port=1 -Dcom.sun.management.jmxremote.ssl=true -Dbrooklyn.jmxmp.ssl.authenticate=true -Dcom.sun.management.jmxremote.authenticate=false -Dbrooklyn.jmxmp.ssl.keyStore=/tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/jmx-keystore -Dbrooklyn.jmxmp.ssl.trustStore=/tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/jmx-truststore}], ExecCmd[customizing brooklyn.entity.java.JavaOptsTest$3[id=CPEr9F4S,displayName=:CPEr]: [export RUN_DIR="/tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S", mkdir -p $RUN_DIR, cd $RUN_DIR, mkdir -p /tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/lib]; {logPrefix=CPEr9F4S@null}; {JAVA_OPTS=-javaagent:/tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/brooklyn-jmxmp-agent-shaded-0.5.0-SNAPSHOT.jar -Xms128m -Xmx512m -XX:MaxPermSize=512m -Dcom.sun.management.jmxremote -Djava.rmi.server.hostname=localhost -Dbrooklyn.jmxmp.port=1 -Dcom.sun.management.jmxremote.ssl=true -Dbrooklyn.jmxmp.ssl.authenticate=true -Dcom.sun.management.jmxremote.authenticate=false -Dbrooklyn.jmxmp.ssl.keyStore=/tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/jmx-keystore -Dbrooklyn.jmxmp.ssl.trustStore=/tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/jmx-truststore}], ExecCmd[launching brooklyn.entity.java.JavaOptsTest$3[id=CPEr9F4S,displayName=:CPEr]: [export RUN_DIR="/tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S", mkdir -p $RUN_DIR, cd $RUN_DIR, echo "launching: java $JAVA_OPTS -cp 'lib/*' my.Main ", java $JAVA_OPTS -cp "lib/*" my.Main  >> /tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/console 2>&1 </dev/null &, echo $! > /tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/pid.txt]; {out=, err=, logPrefix=CPEr9F4S@null}; {JAVA_OPTS=-javaagent:/tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/brooklyn-jmxmp-agent-shaded-0.5.0-SNAPSHOT.jar -Xms128m -Xmx512m -XX:MaxPermSize=512m -Dcom.sun.management.jmxremote -Djava.rmi.server.hostname=localhost -Dbrooklyn.jmxmp.port=1 -Dcom.sun.management.jmxremote.ssl=true -Dbrooklyn.jmxmp.ssl.authenticate=true -Dcom.sun.management.jmxremote.authenticate=false -Dbrooklyn.jmxmp.ssl.keyStore=/tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/jmx-keystore -Dbrooklyn.jmxmp.ssl.trustStore=/tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/jmx-truststore}], ExecCmd[check-running brooklyn.entity.java.JavaOptsTest$3[id=CPEr9F4S,displayName=:CPEr]: [export RUN_DIR="/tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S", mkdir -p $RUN_DIR, cd $RUN_DIR, test -f /tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/pid.txt || exit 1, ps -p `cat /tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/pid.txt`]; {logPrefix=CPEr9F4S@null}; {JAVA_OPTS=-javaagent:/tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/brooklyn-jmxmp-agent-shaded-0.5.0-SNAPSHOT.jar -Xms128m -Xmx512m -XX:MaxPermSize=512m -Dcom.sun.management.jmxremote -Djava.rmi.server.hostname=localhost -Dbrooklyn.jmxmp.port=1 -Dcom.sun.management.jmxremote.ssl=true -Dbrooklyn.jmxmp.ssl.authenticate=true -Dcom.sun.management.jmxremote.authenticate=false -Dbrooklyn.jmxmp.ssl.keyStore=/tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/jmx-keystore -Dbrooklyn.jmxmp.ssl.trustStore=/tmp/brooklyn-alex/apps/P57MMAts/entities/brooklyn.entity.java.JavaOptsTest$3_CPEr9F4S/jmx-truststore}]]


 */
}
