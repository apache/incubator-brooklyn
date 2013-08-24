package brooklyn.util.internal.ssh;

import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.Identifiers;
import brooklyn.util.time.Time;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;

/**
 * Test the performance of different variants of invoking the sshj tool.
 * 
 * Intended for human-invocation and inspection, to see which parts are most expensive.
 */
public abstract class SshToolAbstractPerformanceTest {

    private static final Logger LOG = LoggerFactory.getLogger(SshToolAbstractPerformanceTest.class);
    
    private SshTool tool;
    
    protected abstract SshTool newSshTool(Map<String,?> flags);
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (tool != null) tool.disconnect();
    }

    @Test(groups = {"Integration"})
    public void testConsecutiveConnectAndDisconnect() throws Exception {
        Runnable task = new Runnable() {
            public void run() {
                tool = newSshTool(MutableMap.of("host", "localhost"));
                tool.connect();
                tool.disconnect();
            }
        };
        runMany(task, "connect-disconnect", 10);
    }

    @Test(groups = {"Integration"})
    public void testConsecutiveSmallCommands() throws Exception {
        runExecManyCommands(ImmutableList.of("true"), false, "small-cmd", 10);
    }

    @Test(groups = {"Integration"})
    public void testConsecutiveSmallCommandsWithStdouterr() throws Exception {
        runExecManyCommands(ImmutableList.of("true"), true, "small-cmd-with-stdout", 10);
    }

    @Test(groups = {"Integration"})
    public void testConsecutiveBigStdoutCommands() throws Exception {
        runExecManyCommands(ImmutableList.of("head -c 100000 /dev/urandom"), true, "big-stdout", 10);
    }

    @Test(groups = {"Integration"})
    public void testConsecutiveBigStdinCommands() throws Exception {
        String bigstr = Identifiers.makeRandomId(100000);
        runExecManyCommands(ImmutableList.of("echo "+bigstr+" | wc -c"), true, "big-stdin", 10);
    }

    private void runExecManyCommands(final List<String> cmds, final boolean captureOutAndErr, String context, int iterations) throws Exception {
        Runnable task = new Runnable() {
                @Override public void run() {
                    execScript(cmds, captureOutAndErr);
                }};
        runMany(task, context, iterations);
    }

    private void runMany(Runnable task, String context, int iterations) throws Exception {
        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
        ObjectName osMBeanName = ObjectName.getInstance(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
        long preCpuTime = (Long) mbeanServer.getAttribute(osMBeanName, "ProcessCpuTime");
        Stopwatch stopwatch = new Stopwatch().start();
        
        for (int i = 0; i < iterations; i++) {
            task.run();
            
            long postCpuTime = (Long) mbeanServer.getAttribute(osMBeanName, "ProcessCpuTime");
            long elapsedTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            double fractionCpu = (elapsedTime > 0) ? ((double)postCpuTime-preCpuTime) / TimeUnit.MILLISECONDS.toNanos(elapsedTime) : -1;
            LOG.info("Executing {}; completed {}; took {}; fraction cpu {}", new Object[] {context, (i+1), Time.makeTimeStringRounded(elapsedTime), fractionCpu});
        }
    }

    private int execScript(List<String> cmds, boolean captureOutandErr) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        MutableMap<String,?> flags = (captureOutandErr) ? MutableMap.of("out", out, "err", err) : MutableMap.<String,Object>of();
        
        tool = newSshTool(MutableMap.of("host", "localhost"));
        tool.connect();
        int result = tool.execScript(flags, cmds);
        tool.disconnect();
        
        int outlen = out.toByteArray().length;
        int errlen = out.toByteArray().length;
        if (LOG.isTraceEnabled()) LOG.trace("Executed: result={}; stdout={}; stderr={}", new Object[] {result, outlen, errlen});
        return result;
    }
}
