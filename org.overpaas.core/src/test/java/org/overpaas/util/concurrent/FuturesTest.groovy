package brooklyn.util.internal.task

import static brooklyn.util.internal.task.Futures.*;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

class FuturesTest {
    @Test
    public void testFutures() {
        println ""+System.currentTimeMillis()+" starting 1"
        def qfi1 = new FutureValue({ 1 }, { true });
        QualifiableFuture<Object> qf1 = qfi1;
        long e0 = System.currentTimeMillis()
        def v1 = getBlocking(qf1)
        long e1 = System.currentTimeMillis()
        println ""+System.currentTimeMillis()+" "+v1+" ("+(e1-e0)+")"
        
        println ""+System.currentTimeMillis()+" starting 2"
        def qfi2 = qfi1.when { false }
        new Thread( { def v2 = getBlocking(qfi2, timeout:1*TimeUnit.SECONDS, defaultValue:-1); println ""+System.currentTimeMillis()+" ALT "+v2 } ).start()
        def v2 = getBlocking(qfi2, timeout:1*TimeUnit.SECONDS, defaultValue:-1)
        println ""+System.currentTimeMillis()+" "+v2
        
        println ""+System.currentTimeMillis()+" starting 3"
        def x = [ 0 ]
        def qfi3 = new FutureValue({ x[0] });
        new Thread( { def v3 = getBlocking(qfi3, timeout:1*TimeUnit.SECONDS, defaultValue:-1); println ""+System.currentTimeMillis()+" ALT "+v3 } ).start()
        new Thread( { def v3 = getBlocking(qfi3, timeout:2*TimeUnit.SECONDS, defaultValue:-1); println ""+System.currentTimeMillis()+" ALT2 "+v3 } ).start()
        new Thread( { Thread.sleep(1500); x[0] = 3; } ).start()
        def v3 = getBlocking(qfi3, timeout:2*TimeUnit.SECONDS, defaultValue:-1)
        println ""+System.currentTimeMillis()+" "+v3
        // TODO test, above should finishes in < 2s !
    }
}
