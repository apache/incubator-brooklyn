package brooklyn.test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;

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
            try {
                if (preAttempt()) {
                    int code = TestUtils.urlRespondsStatusCode(url);
                    if (code!=200) {
                        failures.incrementAndGet();
                        onFailure("return code "+code);
                    } else {
                        successes.incrementAndGet();
                    }
                }
            } catch (Exception e) {
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
    public void onFailure(Object problem) {
        if (log != null) {
            log.warn("detected failure at "+getUrl()+": "+problem);
        }
        this.problem = problem;
    }
    /** return false to skip a run */
    public boolean preAttempt() {
        return true;
    }
}