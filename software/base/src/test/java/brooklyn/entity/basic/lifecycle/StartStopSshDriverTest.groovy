package brooklyn.entity.basic.lifecycle

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import groovy.transform.InheritConstructors

import java.lang.management.ManagementFactory
import java.lang.management.ThreadInfo
import java.lang.management.ThreadMXBean
import java.util.List

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.location.basic.SshMachineLocation
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity
import brooklyn.util.internal.StreamGobbler

class StartStopSshDriverTest {

    TestApplication app
    TestEntity entity
    SshMachineLocation sshMachineLocation
    StartStopSshDriver driver

    @BeforeMethod
    public void setUp() {
        app = new TestApplication()
        entity = new TestEntity(app)
        sshMachineLocation = new SshMachineLocation(address:"localhost")
        driver = new BasicStartStopSshDriver(entity, sshMachineLocation)
    }
    
    @Test(groups = [ "Integration" ])
    public void testExecuteDoesNotLeaveRunningStreamGobblerThread() {
        ThreadInfo[] existingThreads = getThreadsCalling(StreamGobbler.class)
        List<Long> existingThreadIds = existingThreads.collect { it.threadId }
        
        List<String> script = ["echo hello"]
        driver.execute(script, "mytest")
        
        executeUntilSucceeds(timeout:10*1000) {
            ThreadInfo[] currentThreads = getThreadsCalling(StreamGobbler.class)
            List<Long> currentThreadIds = currentThreads.collect { it.threadId }
            
            currentThreadIds.removeAll(existingThreadIds)
            assertEquals(currentThreadIds, [])
        }
    }
    
    private List<ThreadInfo> getThreadsCalling(Class<?> clazz) {
        String clazzName = clazz.getCanonicalName()
        List<ThreadInfo> result = []
        ThreadMXBean threadMxbean = ManagementFactory.getThreadMXBean()
        ThreadInfo[] threads = threadMxbean.dumpAllThreads(false, false)
        
        for (ThreadInfo thread : threads) {
            StackTraceElement[] stackTrace = thread.getStackTrace()
            for (StackTraceElement stackTraceElement : stackTrace) {
                if (clazzName == stackTraceElement.getClassName()) {
                    result << thread
                    break
                }
            }
        }
        return result
    }
}

@InheritConstructors
public class BasicStartStopSshDriver extends StartStopSshDriver {
    public boolean isRunning() { true }
    public void stop() {}
    public void install() {}
    public void customize() {}
    public void launch() {}
}
