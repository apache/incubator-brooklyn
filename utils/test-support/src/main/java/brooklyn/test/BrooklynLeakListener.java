package brooklyn.test;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.TestListenerAdapter;

public class BrooklynLeakListener extends TestListenerAdapter {

    private static final Logger TEST_RESOURCE_LOG = LoggerFactory.getLogger("test.resource.usage");
    
    @Override
    public void onStart(ITestContext testContext) {
        super.onStart(testContext);
        TEST_RESOURCE_LOG.info("BrooklynLeakListener.onStart attempting to terminate all extant ManagementContexts: "
                + "name=" + testContext.getName()
                + "; includedGroups="+Arrays.toString(testContext.getIncludedGroups())
                + "; excludedGroups="+Arrays.toString(testContext.getExcludedGroups())
                + "; suiteName="+testContext.getSuite().getName()
                + "; outDir="+testContext.getOutputDirectory());
        tryTerminateAll();
    }
    
    @Override
    public void onFinish(ITestContext testContext) {
        super.onFinish(testContext);
        TEST_RESOURCE_LOG.info("BrooklynLeakListener.onFinish attempting to terminate all extant ManagementContexts: "
                + "name=" + testContext.getName()
                + "; includedGroups="+Arrays.toString(testContext.getIncludedGroups())
                + "; excludedGroups="+Arrays.toString(testContext.getExcludedGroups())
                + "; suiteName="+testContext.getSuite().getName()
                + "; outDir="+testContext.getOutputDirectory());
        tryTerminateAll();
    }
    
    /**
     * Tries to reflectively invoke {@code LocalManagementContext.logAll(TEST_RESOURCE_LOG); LocalManagementContext.terminateAll()}.
     * 
     * It does this reflectively because the listener is executed for all projects, included those that don't
     * depend on brooklyn-core, so LocalManagementContext may not be on the classpath.
     */
    private void tryTerminateAll() {
        String clazzName = "brooklyn.management.internal.LocalManagementContext";
        try {
            Class<?> clazz = BrooklynLeakListener.class.getClassLoader().loadClass(clazzName);
            clazz.getMethod("logAll", new Class[] {Logger.class}).invoke(null, TEST_RESOURCE_LOG);
            clazz.getMethod("terminateAll").invoke(null);
        } catch (ClassNotFoundException e) {
            TEST_RESOURCE_LOG.info("Class {} not found in testng listener, so not attempting to terminate all extant ManagementContexts; continuing", clazzName);
        } catch (Throwable e) {
            TEST_RESOURCE_LOG.error("ERROR in testng listener, attempting to terminate all extant ManagementContexts", e);
        }
    }
}
