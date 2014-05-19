package brooklyn.util.time;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Durations {

    public static boolean await(CountDownLatch latch, Duration time) throws InterruptedException {
        return latch.await(time.toNanoseconds(), TimeUnit.NANOSECONDS);
    }
    
    public static void join(Thread thread, Duration time) throws InterruptedException {
        thread.join(time.toMillisecondsRoundingUp());
    }
    
}
