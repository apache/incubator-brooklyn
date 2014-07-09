package brooklyn.test;

import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;
import org.testng.SkipException;

public class PlatformTestSelectorListener implements IInvokedMethodListener {
    private static final String GROUP_UNIX = "UNIX";
    private static final String GROUP_WINDOWS = "Windows";

    public static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        boolean isUnixTest = false;
        boolean isWinTest = false;
        
        String[] groups = method.getTestMethod().getGroups();
        for (String group : groups) {
            isUnixTest = isUnixTest || group.equalsIgnoreCase(GROUP_UNIX);
            isWinTest = isWinTest || group.equalsIgnoreCase(GROUP_WINDOWS);
        }
        
        boolean isWinPlatform = isWindows();
        if (isUnixTest || isWinTest) {
            if (isWinPlatform && isUnixTest && !isWinTest) {
                throw new SkipException("Skipping unix-specific test."); 
            } else if (!isWinPlatform && isWinTest && !isUnixTest) {
                throw new SkipException("Skipping windows-specific test."); 
            }
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {}
}
