package brooklyn.management.internal.task

import static brooklyn.management.internal.task.Futures.*

import org.testng.annotations.Test

import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.EffectorSayHiTest

/**
 * Test the operation of the {@link Futures} factory utilities.
 * 
 * TODO clarify test purpose
 * TODO add assertions
 */
public class FuturesTest {
    private static final Logger log = LoggerFactory.getLogger(FuturesTest.class)
 
    @Test
    public void testFutures() {
        log.debug "Starting 1 - {}", System.currentTimeMillis()
        def qfi1 = new FutureValue({ 1 }, { true });
        QualifiableFuture<Object> qf1 = qfi1;
        long e0 = System.currentTimeMillis()
        def v1 = getBlocking(qf1)
        long e1 = System.currentTimeMillis()
        log.debug "{} {} ({})", System.currentTimeMillis(), v1, (e1-e0)
        
        log.debug "Starting 2 - {}", System.currentTimeMillis()
        def qfi2 = qfi1.when { false }
        new Thread({
                def v2 = getBlocking(qfi2, timeout:1*TimeUnit.SECONDS, defaultValue:-1)
                log.debug "{} ALT {}", System.currentTimeMillis(), v2
            }).start()
        def v2 = getBlocking(qfi2, timeout:1*TimeUnit.SECONDS, defaultValue:-1)
        log.debug "{} {}", System.currentTimeMillis(), v2
        
        log.debug "Starting 3 - {}", System.currentTimeMillis()
        def x = [ 0 ]
        def qfi3 = new FutureValue({ x[0] });
        new Thread({
                def v3 = getBlocking(qfi3, timeout:1*TimeUnit.SECONDS, defaultValue:-1)
                log.debug "{} ALT {}", System.currentTimeMillis(), v3
            }).start()
        new Thread({
                def v3 = getBlocking(qfi3, timeout:2*TimeUnit.SECONDS, defaultValue:-1)
                log.debug "{} ALT {}", System.currentTimeMillis(), v3
            }).start()
        new Thread({ Thread.sleep(1500); x[0] = 3; }).start()
        def v3 = getBlocking(qfi3, timeout:2*TimeUnit.SECONDS, defaultValue:-1)
        log.debug "{} {}", System.currentTimeMillis(), v3
        // TODO test, above should finishes in < 2s !
    }
}
