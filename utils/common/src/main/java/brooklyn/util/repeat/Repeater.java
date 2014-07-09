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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.CountdownTimer;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.util.concurrent.Callables;

/**
 * Simple DSL to repeat a fragment of code periodically until a condition is satisfied.
 *
 * In its simplest case, it is passed two {@link groovy.lang.Closure}s / {@link Callable} - 
 * the first is executed, then the second. If the second closure returns false, the loop
 * is repeated; if true, it finishes. Further customization can be applied to set the period 
 * between loops and place a maximum limit on how long the loop should run for.
 * <p>
 * It is configured in a <em>fluent</em> manner. For example, in Groovy:
 * <pre>
 * {@code
 * Repeater.create("Wait until the Frobnitzer is ready")
 *     .repeat {
 *         status = frobnitzer.getStatus()
 *     }
 *     .until {
 *         status == "Ready" || status == "Failed"
 *     }
 *     .limitIterationsTo(30)
 *     .run()
 * }
 * </pre>
 * 
 * Or in Java:
 * <pre>
 * {@code
 * Repeater.create("Wait until the Frobnitzer is ready")
 *     .until(new Callable<Boolean>() {
 *              public Boolean call() {
 *                  String status = frobnitzer.getStatus()
 *                  return "Ready".equals(status) || "Failed".equals(status);
 *              }})
 *     .limitIterationsTo(30)
 *     .run()
 * }
 * </pre>
 */
public class Repeater {

    private static final Logger log = LoggerFactory.getLogger(Repeater.class);

    private final String description;
    private Callable<?> body = Callables.returning(null);
    private Callable<Boolean> exitCondition;
    private Function<? super Integer,Duration> delayOnIteration = null;
    private Duration timeLimit = null;
    private int iterationLimit = 0;
    private boolean rethrowException = false;
    private boolean rethrowExceptionImmediately = false;
    private boolean warnOnUnRethrownException = true;

    public Repeater() {
        this(null);
    }

    /**
     * Construct a new instance of Repeater.
     *
     * @param description a description of the operation that will appear in debug logs.
     */
    public Repeater(String description) {
        this.description = description != null ? description : "Repeater";
    }

    public static Repeater create() {
        return create(null);
    }
    public static Repeater create(String description) {
        return new Repeater(description);
    }

    /**
     * Sets the main body of the loop to be a no-op; useful if using {@link #until(Callable)} instead
     * 
     * @return {@literal this} to aid coding in a fluent style.
     * @deprecated since 0.7.0 this is no-op, as the repeater defaults to repeating nothing, simply remove the call,
     * using just <code>Repeater.until(...)</code>.
     */
    public Repeater repeat() {
        return repeat(Callables.returning(null));
    }

    /**
     * Sets the main body of the loop.
     *
     * @param body a closure or other Runnable that is executed in the main body of the loop.
     * @return {@literal this} to aid coding in a fluent style.
     */
    public Repeater repeat(Runnable body) {
        checkNotNull(body, "body must not be null");
        this.body = (body instanceof Callable) ? (Callable<?>)body : Executors.callable(body);
        return this;
    }

    /**
     * Sets the main body of the loop.
     *
     * @param body a closure or other Callable that is executed in the main body of the loop.
     * @return {@literal this} to aid coding in a fluent style.
     */
    public Repeater repeat(Callable<?> body) {
        checkNotNull(body, "body must not be null");
        this.body = body;
        return this;
    }

    /**
     * Set how long to wait between loop iterations.
     *
     * @param period how long to wait between loop iterations.
     * @param unit the unit of measurement of the period.
     * @return {@literal this} to aid coding in a fluent style.
     */
    public Repeater every(long period, TimeUnit unit) {
        return every(Duration.of(period, unit));
    }

    /**
     * Set how long to wait between loop iterations, as a constant function in {@link #delayOnIteration}
     */
    public Repeater every(Duration duration) {
        Preconditions.checkNotNull(duration, "duration must not be null");
        Preconditions.checkArgument(duration.toMilliseconds()>0, "period must be positive: %s", duration);
        return delayOnIteration(Functions.constant(duration));
    }

    public Repeater every(groovy.time.Duration duration) {
        return every(Duration.of(duration));
    }
    
    /** sets a function which determines how long to delay on a given iteration between checks,
     * with 0 being mapped to the initial delay (after the initial check) */
    public Repeater delayOnIteration(Function<? super Integer,Duration> delayFunction) {
        Preconditions.checkNotNull(delayFunction, "delayFunction must not be null");
        this.delayOnIteration = delayFunction;
        return this;
    }

    /** sets the {@link #delayOnIteration(Function)} function to be an exponential backoff as follows:
     * @param initialDelay  the delay on the first iteration, after the initial check
     * @param multiplier  the rate at which to increase the loop delay, must be >= 1
     * @param finalDelay  an optional cap on the loop delay   */
    public Repeater backoff(final Duration initialDelay, final double multiplier, @Nullable final Duration finalDelay) {
        Preconditions.checkNotNull(initialDelay, "initialDelay");
        Preconditions.checkArgument(multiplier>=1.0, "multiplier >= 1.0");
        return delayOnIteration(new Function<Integer, Duration>() {
            @Override
            public Duration apply(Integer iteration) {
                /* we iterate because otherwise we risk overflow errors by using multiplier^iteration; 
                 * e.g. with:
                 * return Duration.min(initialDelay.multiply(Math.pow(multiplier, iteration)), finalDelay); */
                Duration result = initialDelay;
                for (int i=0; i<iteration; i++) {
                    result = result.multiply(multiplier);
                    if (finalDelay!=null && result.compareTo(finalDelay)>0)
                        return finalDelay;
                }
                return result;
            }
        });
    }

    /** convenience to start with a 10ms delay and exponentially back-off at a rate of 1.2 
     * up to a max per-iteration delay as supplied here.
     * 1.2 chosen because it decays nicely, going from 10ms to 1s in approx 25 iterations totalling 5s elapsed time. */
    public Repeater backoffTo(final Duration finalDelay) {
        return backoff(Duration.millis(10), 1.2, finalDelay);
    }

    /**
     * Set code fragment that tests if the loop has completed.
     *
     * @param exitCondition a closure or other Callable that returns a boolean. If this code returns {@literal true} then the
     * loop will stop executing.
     * @return {@literal this} to aid coding in a fluent style.
     */
    public Repeater until(Callable<Boolean> exitCondition) {
        Preconditions.checkNotNull(exitCondition, "exitCondition must not be null");
        this.exitCondition = exitCondition;
        return this;
    }

    public <T> Repeater until(final T target, final Predicate<T> exitCondition) {
        Preconditions.checkNotNull(exitCondition, "exitCondition must not be null");
        return until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return exitCondition.apply(target);
            }
        });
    }

    /**
     * If the exit condition check throws an exception, it will be recorded and the last exception will be thrown on failure.
     *
     * @return {@literal this} to aid coding in a fluent style.
     */
    public Repeater rethrowException() {
        this.rethrowException = true;
        return this;
    }

    /**
     * If the repeated body or the exit condition check throws an exception, then propagate that exception immediately.
     *
     * @return {@literal this} to aid coding in a fluent style.
     */
    public Repeater rethrowExceptionImmediately() {
        this.rethrowExceptionImmediately = true;
        return this;
    }

    public Repeater suppressWarnings() {
        this.warnOnUnRethrownException = false;
        return this;
    }

    /**
     * Set the maximum number of iterations.
     *
     * The loop will exit if the condition has not been satisfied after this number of iterations.
     *
     * @param iterationLimit the maximum number of iterations.
     * @return {@literal this} to aid coding in a fluent style.
     */
    public Repeater limitIterationsTo(int iterationLimit) {
        Preconditions.checkArgument(iterationLimit > 0, "iterationLimit must be positive: %s", iterationLimit);
        this.iterationLimit = iterationLimit;
        return this;
    }

    /**
     * @see #limitTimeTo(Duration)
     * 
     * @param deadline the time that the loop should wait.
     * @param unit the unit of measurement of the period.
     * @return {@literal this} to aid coding in a fluent style.
     */
    public Repeater limitTimeTo(long deadline, TimeUnit unit) {
        return limitTimeTo(Duration.of(deadline, unit));
    }

    /**
     * Set the amount of time to wait for the condition.
     * The repeater will wait at least this long for the condition to be true,
     * and will exit soon after even if the condition is false.
     */
    public Repeater limitTimeTo(Duration duration) {
        Preconditions.checkNotNull(duration, "duration must not be null");
        Preconditions.checkArgument(duration.toMilliseconds() > 0, "deadline must be positive: %s", duration);
        this.timeLimit = duration;
        return this;
    }

    /**
     * Run the loop.
     *
     * @return true if the exit condition was satisfied; false if the loop terminated for any other reason.
     */
    public boolean run() {
        Preconditions.checkState(body != null, "repeat() method has not been called to set the body");
        Preconditions.checkState(exitCondition != null, "until() method has not been called to set the exit condition");
        Preconditions.checkState(delayOnIteration != null, "every() method (or other delaySupplier() / backoff() method) has not been called to set the loop delay");

        Throwable lastError = null;
        int iterations = 0;
        CountdownTimer timer = timeLimit!=null ? CountdownTimer.newInstanceStarted(timeLimit) : CountdownTimer.newInstancePaused(Duration.PRACTICALLY_FOREVER);

        while (true) {
            Duration delayThisIteration = delayOnIteration.apply(iterations);
            iterations++;

            try {
                body.call();
            } catch (Exception e) {
                log.warn(description, e);
                if (rethrowExceptionImmediately) throw Exceptions.propagate(e);
            }

            boolean done = false;
            try {
                lastError = null;
                done = exitCondition.call();
            } catch (Exception e) {
                if (log.isDebugEnabled()) log.debug(description, e);
                lastError = e;
                if (rethrowExceptionImmediately) throw Exceptions.propagate(e);
            }
            if (done) {
                if (log.isDebugEnabled()) log.debug("{}: condition satisfied", description);
                return true;
            } else {
                if (log.isDebugEnabled()) {
                    String msg = String.format("%s: unsatisfied during iteration %s %s", description, iterations,
                        (iterationLimit > 0 ? "(max "+iterationLimit+" attempts)" : "") + 
                        (timer.isRunning() ? "("+Time.makeTimeStringRounded(timer.getDurationRemaining())+" remaining)" : ""));
                    if (iterations == 1) {
                        log.debug(msg);
                    } else {
                        log.trace(msg);
                    }
                }
            }

            if (iterationLimit > 0 && iterations >= iterationLimit) {
                if (log.isDebugEnabled()) log.debug("{}: condition not satisfied and exceeded iteration limit", description);
                if (rethrowException && lastError != null) {
                    log.warn("{}: error caught checking condition (rethrowing): {}", description, lastError.getMessage());
                    throw Exceptions.propagate(lastError);
                }
                if (warnOnUnRethrownException && lastError != null)
                    log.warn("{}: error caught checking condition: {}", description, lastError.getMessage());
                return false;
            }

            if (timer.isExpired()) {
                if (log.isDebugEnabled()) log.debug("{}: condition not satisfied, with {} elapsed (limit {})", 
                    new Object[] { description, Time.makeTimeStringRounded(timer.getDurationElapsed()), Time.makeTimeStringRounded(timeLimit) });
                if (rethrowException && lastError != null) {
                    log.error("{}: error caught checking condition: {}", description, lastError.getMessage());
                    throw Exceptions.propagate(lastError);
                }
                return false;
            }

            Time.sleep(delayThisIteration);
        }
    }
}
