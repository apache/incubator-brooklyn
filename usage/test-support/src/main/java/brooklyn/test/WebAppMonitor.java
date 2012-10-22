package brooklyn.test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.testng.Assert;

/**
 * Repeatedly polls a given URL, to check if it is always available.
 * 
 * @author Alex, Aled
 */
public class WebAppMonitor implements Runnable {
    final AtomicBoolean shouldBeActive = new AtomicBoolean(true);
    final AtomicBoolean isActive = new AtomicBoolean(false);
    final AtomicInteger successes = new AtomicInteger(0);
    final AtomicInteger failures = new AtomicInteger(0);
    final AtomicLong lastTime = new AtomicLong(-1);
    final AtomicReference<Object> lastStatus = new AtomicReference<Object>(null);
    final AtomicReference<Object> lastFailure = new AtomicReference<Object>(null);
    Logger log;
    Object problem = null; 
    String url;
    long delayMillis = 500;
    
    public WebAppMonitor(String url) {
        this.url = url;
    }
    public WebAppMonitor() {
    }
    public WebAppMonitor logFailures(Logger log) {
        this.log = log;
        return this;
    }
    public WebAppMonitor delayMillis(long val) {
        this.delayMillis = val;
        return this;
    }
    public WebAppMonitor url(String val) {
        this.url = val;
        return this;
    }
    
    public void run() {
        synchronized (isActive) {
            if (isActive.getAndSet(true))
                throw new IllegalStateException("already running");
        }
        while (shouldBeActive.get()) {
            long startTime = System.currentTimeMillis();
            try {
                if (preAttempt()) {
                    int code = HttpTestUtils.getHttpStatusCode(url);
                    lastTime.set(System.currentTimeMillis()-startTime);
                    lastStatus.set(code);
                    if (isResponseOkay(code)) {
                        lastFailure.set(code);
                        failures.incrementAndGet();
                        onFailure("return code "+code);
                    } else {
                        successes.incrementAndGet();
                    }
                }
            } catch (Exception e) {
                lastTime.set(System.currentTimeMillis()-startTime);
                lastStatus.set(e);
                lastFailure.set(e);
                failures.incrementAndGet();
                onFailure(e);
            }
            try {
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
            } catch (InterruptedException e) {
                onFailure(e);
                shouldBeActive.set(false);
            }
        }
        synchronized (isActive) {
            if (!isActive.getAndSet(false))
                throw new IllegalStateException("shouldn't be possible!");
            isActive.notifyAll();
        }
    }
    
    public boolean isResponseOkay(Object code) {
        return code!=null && new Integer(200).equals(code);
    }
    
    public void setDelayMillis(long delayMillis) {
        this.delayMillis = delayMillis;
    }
    public long getDelayMillis() {
        return delayMillis;
    }
    public void terminate() throws InterruptedException {
        shouldBeActive.set(false);
        synchronized (isActive) {
            while (isActive.get()) isActive.wait();
        }
    }
    public int getFailures() {
        return failures.get();
    }
    public int getSuccesses() {
        return successes.get();
    }
    public void setUrl(String url) {
        this.url = url;
    }
    public String getUrl() {
        return url;
    }
    public Object getProblem() {
        return problem;
    }
    public int getAttempts() {
        return getFailures()+getSuccesses();
    }
    public boolean getLastWasFailed() {
        return isResponseOkay(getLastStatus());
    }
    public Object getLastStatus() {
        return lastStatus.get();
    }
    public long getLastTime() {
        return lastTime.get();
    }
    /** result code (int) or exception */
    public Object getLastFailure() {
        return lastFailure.get();
    }
    
    public void onFailure(Object problem) {
        if (log != null) {
            log.warn("Detected failure in monitor accessing "+getUrl()+": "+problem);
        }
        this.problem = problem;
    }
    
    /** return false to skip a run */
    public boolean preAttempt() {
        return true;
    }
    
    public WebAppMonitor assertNoFailures(String message) {
        return assertSuccessFraction(message, 1.0);
    }
    public WebAppMonitor assertAttemptsMade(int minAttempts, String message) {
        if (getAttempts()<minAttempts) {
            Assert.fail(message+" -- webapp access failures! " +
                    "(0 attempts made; probably blocked on server)");            
        }
        return this;
    }
    public WebAppMonitor assertSuccessFraction(String message, double percentage) {
        if ((getFailures() > (1-percentage) * getAttempts()+0.0001) || getAttempts()<=0) {
            Assert.fail(message+" -- webapp access failures! " +
            		"("+getFailures()+" failed of "+getAttempts()+" monitoring attempts) against "+getUrl()+"; " +
            		"last was "+getLastStatus()+" taking "+getLastTime()+"ms" +
            		(getLastFailure()!=null ? "; last failure was "+getLastFailure() : ""));
        }
        return this;
    }
    public WebAppMonitor resetCounts() {
        failures.set(0);
        successes.set(0);
        return this;
    }
    
}