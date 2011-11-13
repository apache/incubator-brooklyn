package brooklyn.test;

import java.util.concurrent.atomic.AtomicInteger;

import org.testng.IClass;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * adapted from the following class:
 * 
 * @see org.jclouds.test.testng.UnitTestStatusListener
 */
public class StatusListener implements ITestListener {
    /**
     * Holds test classes actually running in all threads.
     */
    private ThreadLocal<IClass> threadTestClass = new ThreadLocal<IClass>();
    private ThreadLocal<Long> threadTestStart = new ThreadLocal<Long>();

    private AtomicInteger failed = new AtomicInteger(0);
    private AtomicInteger succeded = new AtomicInteger(0);
    private AtomicInteger skipped = new AtomicInteger(0);

    //TODO instead of system.out.println we should log -- *and* perhaps write to sysout if logger doesn't?
    
    public void onTestStart(ITestResult res) {
        System.out.println("Starting test " + getTestDesc(res));
        threadTestClass.set(res.getTestClass());
        threadTestStart.set(System.currentTimeMillis());
    }

    synchronized public void onTestSuccess(ITestResult arg0) {
        System.out.println(getThreadId() + " Test " + getTestDesc(arg0) + " succeeded: " + (System.currentTimeMillis() - threadTestStart.get()) + "ms");
        succeded.incrementAndGet();
        printStatus();
    }

    synchronized public void onTestFailure(ITestResult arg0) {
        System.out.println(getThreadId() + " Test " + getTestDesc(arg0) + " failed.");
        failed.incrementAndGet();
        printStatus();
    }

    synchronized public void onTestSkipped(ITestResult arg0) {
        System.out.println(getThreadId() + " Test " + getTestDesc(arg0) + " skipped.");
        skipped.incrementAndGet();
        printStatus();
    }

    public void onTestFailedButWithinSuccessPercentage(ITestResult arg0) {
    }

    public void onStart(ITestContext arg0) {
    }

    public void onFinish(ITestContext arg0) {
    }

    private String getThreadId() {
        return "[" + Thread.currentThread().getName() + "]";
    }

    private String getTestDesc(ITestResult res) {
        return res.getMethod().getMethodName() + "(" + res.getTestClass().getName() + ")";
    }

    private void printStatus() {
        System.out.println("Test suite progress: tests succeeded: " + succeded.get() + ", failed: " + failed.get() + ", skipped: " + skipped.get() + ".");
    }
}
