package brooklyn.util.internal.ssh;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.exceptions.Exceptions;

/**
 * Allow replayable request to be retried a limited number of times, and impose an exponential back-off
 * delay before returning.
 * <p>
 * Copied and modified from jclouds; original author was James Murty
 */
public class BackoffLimitedRetryHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SshjTool.class);

    private final int retryCountLimit;

    private final long delayStart;

    public BackoffLimitedRetryHandler() {
        this(5, 50L);
    }
    
    public BackoffLimitedRetryHandler(int retryCountLimit, long delayStart) {
        this.retryCountLimit = retryCountLimit;
        this.delayStart = delayStart;
    }
    
    public void imposeBackoffExponentialDelay(int failureCount, String commandDescription) {
        imposeBackoffExponentialDelay(delayStart, 2, failureCount, retryCountLimit, commandDescription);
    }

    public void imposeBackoffExponentialDelay(long period, int pow, int failureCount, int max, String commandDescription) {
        imposeBackoffExponentialDelay(period, period * 10l, pow, failureCount, max, commandDescription);
    }

    public void imposeBackoffExponentialDelay(long period,
            long maxPeriod,
            int pow,
            int failureCount,
            int max,
            String commandDescription) {
        long delayMs = (long) (period * Math.pow(failureCount, pow));
        delayMs = (delayMs > maxPeriod) ? maxPeriod : delayMs;
        if (LOG.isDebugEnabled()) LOG.debug("Retry {}/{}: delaying for {} ms: {}", 
                new Object[] {failureCount, max, delayMs, commandDescription});
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Exceptions.propagate(e);
        }
    }

}
