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
package brooklyn.util.repeat;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

import brooklyn.util.time.Duration;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Callables;

public class RepeaterTest {

    @Test
    public void sanityTest() {
        new Repeater("Sanity test")
            .until(Callables.returning(true))
            .every(Duration.millis(10));
    }

    @Test
    public void sanityTestDescription() {
        new Repeater()
            .until(Callables.returning(true))
            .every(Duration.millis(10));
    }

    @Test
    public void sanityTestBuilder() {
        Repeater.create("Sanity test")
            .until(Callables.returning(true))
            .every(Duration.millis(10));
    }

    @Test
    public void sanityTestBuilderDescription() {
        Repeater.create()
            .until(Callables.returning(true))
            .every(Duration.millis(10));
    }

    @Test
    public void repeatSucceedsIfClosureIsNonNull() {
        new Repeater("repeatSucceedsIfClosureIsNonNull").repeat(Callables.returning(true));
    }

    @Test(expectedExceptions = { NullPointerException.class, IllegalArgumentException.class })
    public void untilFailsIfClosureIsNull() {
        new Repeater("untilFailsIfClosureIsNull").until(null);
        fail("Expected exception was not thrown");
    }

    @Test
    public void untilSucceedsIfClosureIsNonNull() {
        new Repeater("untilSucceedsIfClosureIsNonNull")
            .until(Callables.returning(true));
    }

    @Test(expectedExceptions = { NullPointerException.class, IllegalArgumentException.class })
    public void everyFailsIfPeriodIsZero() {
        new Repeater("everyFailsIfPeriodIsZero").every(Duration.ZERO);
        fail("Expected exception was not thrown");
    }

    @Test(expectedExceptions = { NullPointerException.class, IllegalArgumentException.class })
    public void everyFailsIfPeriodIsNegative() {
        new Repeater("everyFailsIfPeriodIsNegative").every(Duration.millis(-1));
        fail("Expected exception was not thrown");
    }

    @Test(expectedExceptions = { NullPointerException.class, IllegalArgumentException.class })
    public void everyFailsIfUnitsIsNull() {
        new Repeater("everyFailsIfUnitsIsNull").every(10, null);
        fail("Expected exception was not thrown");
    }

    @Test
    public void everySucceedsIfPeriodIsPositiveAndUnitsIsNonNull() {
        new Repeater("repeatSucceedsIfClosureIsNonNull").every(Duration.millis(10));
    }

    @Test(expectedExceptions = { NullPointerException.class, IllegalArgumentException.class })
    public void limitTimeToFailsIfPeriodIsZero() {
        new Repeater("limitTimeToFailsIfPeriodIsZero").limitTimeTo(0, TimeUnit.MILLISECONDS);
        fail("Expected exception was not thrown");
    }

    @Test(expectedExceptions = { NullPointerException.class, IllegalArgumentException.class })
    public void limitTimeToFailsIfPeriodIsNegative() {
        new Repeater("limitTimeToFailsIfPeriodIsNegative").limitTimeTo(-1, TimeUnit.MILLISECONDS);
        fail("Expected exception was not thrown");
    }

    @Test(expectedExceptions = { NullPointerException.class, IllegalArgumentException.class })
    public void limitTimeToFailsIfUnitsIsNull() {
        new Repeater("limitTimeToFailsIfUnitsIsNull").limitTimeTo(10, null);
        fail("Expected exception was not thrown");
    }

    @Test
    public void limitTimeToSucceedsIfPeriodIsPositiveAndUnitsIsNonNull() {
        new Repeater("limitTimeToSucceedsIfClosureIsNonNull").limitTimeTo(10, TimeUnit.MILLISECONDS);
    }

    @Test
    public void everyAcceptsDuration() {
        new Repeater("everyAcceptsDuration").every(Duration.ONE_SECOND);
    }

    @Test
    public void everyAcceptsTimeUnit() {
        new Repeater("everyAcceptsTimeUnit").every(1000000L, TimeUnit.MICROSECONDS);
    }

    @Test
    public void runReturnsTrueIfExitConditionIsTrue() {
        assertTrue(new Repeater("runReturnsTrueIfExitConditionIsTrue")
            .every(Duration.millis(1))
            .until(Callables.returning(true))
            .run());
    }

    @Test
    public void runRespectsMaximumIterationLimitAndReturnsFalseIfReached() {
        final AtomicInteger iterations = new AtomicInteger();
        assertFalse(new Repeater("runRespectsMaximumIterationLimitAndReturnsFalseIfReached")
            .repeat(new Runnable() { public void run() { iterations.incrementAndGet(); } })
            .every(Duration.millis(1))
            .until(Callables.returning(false))
            .limitIterationsTo(5)
            .run());
        assertEquals(iterations.get(), 5);
    }

    /**
     * Check that the {@link Repeater} will stop after a time limit.
     *
     * The repeater is configured to run every 100ms and never stop until the limit is reached.
     * This is given as {@link Repeater#limitTimeTo(groovy.time.Duration)} and the execution time
     * is then checked to ensure it is between 100% and 400% of the specified value. Due to scheduling
     * delays and other factors in a non RTOS system it is expected that the repeater will take much
     * longer to exit occasionally.
     *
     * @see #runRespectsMaximumIterationLimitAndReturnsFalseIfReached()
     */
    @Test(groups="Integration")
    public void runRespectsTimeLimitAndReturnsFalseIfReached() {
        final long LIMIT = 2000l;
        Repeater repeater = new Repeater("runRespectsTimeLimitAndReturnsFalseIfReached")
            .every(Duration.millis(100))
            .until(Callables.returning(false))
            .limitTimeTo(LIMIT, TimeUnit.MILLISECONDS);

        Stopwatch stopwatch = Stopwatch.createStarted();
        boolean result = repeater.run();
        stopwatch.stop();

        assertFalse(result);

        long difference = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        assertTrue(difference >= LIMIT, "Difference was: " + difference);
        assertTrue(difference < 4 * LIMIT, "Difference was: " + difference);
    }

    @Test(expectedExceptions = { IllegalStateException.class })
    public void runFailsIfUntilWasNotSet() {
        new Repeater("runFailsIfUntilWasNotSet")
            .every(Duration.millis(10))
            .run();
        fail("Expected exception was not thrown");
    }

    @Test(expectedExceptions = { IllegalStateException.class })
    public void runFailsIfEveryWasNotSet() {
        new Repeater("runFailsIfEveryWasNotSet")
            .until(Callables.returning(true))
            .run();
        fail("Expected exception was not thrown");
    }

    @Test(expectedExceptions = { UnsupportedOperationException.class })
    public void testRethrowsException() {
        new Repeater("throwRuntimeException")
            .every(Duration.millis(10))
            .until(callableThrowingUnsupportedFail())
            .rethrowException()
            .limitIterationsTo(2)
            .run();
        fail("Expected exception was not thrown");
    }

    @Test
    public void testNoRethrowsException() {
        try {
	        boolean result = new Repeater("throwRuntimeException")
	            .every(Duration.millis(10))
	            .until(callableThrowingUnsupportedFail())
	            .limitIterationsTo(2)
	            .run();
	        assertFalse(result);
        } catch (RuntimeException re) {
            fail("Exception should not have been thrown: " + re.getMessage());
        }
    }
	
	private static Callable<Boolean> callableThrowingUnsupportedFail() {
        return new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                throw new UnsupportedOperationException("fail");
            }
        };
    }
	
}
