/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.util.time;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;

public class Durations {

    public static boolean await(CountDownLatch latch, Duration time) throws InterruptedException {
        return latch.await(time.toNanoseconds(), TimeUnit.NANOSECONDS);
    }
    
    public static void join(Thread thread, Duration time) throws InterruptedException {
        thread.join(time.toMillisecondsRoundingUp());
    }

    public static <T> Maybe<T> get(Future<T> t, Duration timeout) {
        try {
            if (timeout==null || timeout.toMilliseconds()<0 || Duration.PRACTICALLY_FOREVER.equals(timeout))
                return Maybe.of(t.get());
            if (timeout.toMilliseconds()==0 && !t.isDone())
                return Maybe.absent("Task "+t+" not completed when immediate completion requested");
            return Maybe.of(t.get(timeout.toMilliseconds(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            return Maybe.absent("Task "+t+" did not complete within "+timeout);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    public static <T> Maybe<T> get(Future<T> t, CountdownTimer timer) {
        if (timer==null) return get(t, (Duration)null);
        Duration remaining = timer.getDurationRemaining();
        if (remaining.isPositive()) return get(t, remaining);
        return get(t, Duration.ZERO);
    }
    
}
