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
package brooklyn.util.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.JavaGroovyEquivalents;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Preconditions;
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
 * 
 * @deprecated since 0.7.0, use {@link brooklyn.util.repeat.Repeater} instead
 */
@Deprecated
public class Repeater {
    
    // TODO Was converted to Java, from groovy. Needs thorough review and improvements
    // to use idiomatic java
    
    private static final Logger log = LoggerFactory.getLogger(Repeater.class);

    static { TimeExtras.init(); }

	@SetFromFlag
    private final String description;
    private Callable<?> body = Callables.returning(null);
    private Callable<Boolean> exitCondition;
	@SetFromFlag
	private Long period = null;
	@SetFromFlag("timeout")
	private Long durationLimit = null;
    private int iterationLimit = 0;
    private boolean rethrowException = false;
    private boolean rethrowExceptionImmediately = false;
	private boolean warnOnUnRethrownException = true;

	public Repeater() {
	    this(MutableMap.of(), null);
    }

    public Repeater(Map<?,?> flags) {
        this(flags, null);
    }

    public Repeater(String description) {
        this(MutableMap.of(), description);
    }
    
    /**
     * Construct a new instance of Repeater.
     *
     * @param flags       can include period, timeout, description
     * @param description a description of the operation that will appear in debug logs.
     */
    public Repeater(Map<?,?> flags, String description) {
    	setFromFlags(flags);
    	this.description = JavaGroovyEquivalents.elvis(description, this.description, "Repeater");
    }

	public void setFromFlags(Map<?,?> flags) {
		FlagUtils.setFieldsFromFlags(flags, this);
	}
	
    public static Repeater create() {
        return create(MutableMap.of());
    }
	public static Repeater create(Map<?,?> flags) {
		return create(flags, null);
	}
    public static Repeater create(String description) {
        return create(MutableMap.of(), description);
    }
    public static Repeater create(Map<?,?> flags, String description) {
        return new Repeater(flags, description);
    }

    /**
     * Sets the main body of the loop to be a no-op.
     * 
     * @return {@literal this} to aid coding in a fluent style.
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
        Preconditions.checkArgument(period > 0, "period must be positive: %s", period);
        checkNotNull(unit, "unit must not be null");
        this.period = unit.toMillis(period);
        return this;
    }

    /**
     * @see #every(long, TimeUnit)
     */
    public Repeater every(Duration duration) {
        Preconditions.checkNotNull(duration, "duration must not be null");
		Preconditions.checkArgument(duration.toMilliseconds()>0, "period must be positive: %s", duration);
		this.period = duration.toMilliseconds();
		return this;
    }
    
    public Repeater every(groovy.time.Duration duration) {
        return every(Duration.of(duration));
    }

    /**
     * @see #every(long, TimeUnit)
	 * @deprecated specify unit
     */
    public Repeater every(long duration) {
        return every(duration, TimeUnit.MILLISECONDS);
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
     * Set the amount of time to wait for the condition.
     * The repeater will wait at least this long for the condition to be true,
     * and will exit soon after even if the condition is false.
     *
     * @param deadline the time that the loop should wait.
     * @param unit the unit of measurement of the period.
     * @return {@literal this} to aid coding in a fluent style.
     */
    public Repeater limitTimeTo(long deadline, TimeUnit unit) {
        Preconditions.checkArgument(deadline > 0, "deadline must be positive: %s", deadline);
        Preconditions.checkNotNull(unit, "unit must not be null");
        this.durationLimit = unit.toMillis(deadline);
        return this;
    }

    /**
     * @see #limitTimeTo(long, TimeUnit)
     */
    public Repeater limitTimeTo(Duration duration) {
        Preconditions.checkNotNull(duration, "duration must not be null");
		Preconditions.checkArgument(duration.toMilliseconds() > 0, "deadline must be positive: %s", duration);
		this.durationLimit = duration.toMilliseconds();
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
        Preconditions.checkState(period != null, "every() method has not been called to set the loop period time units");

        Throwable lastError = null;
        int iterations = 0;
		long endTime = -1;
        if (durationLimit != null) {
			endTime = System.currentTimeMillis() + durationLimit;
        }

        while (true) {
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
                            (endTime > 0 ? "("+Time.makeTimeStringRounded(endTime - System.currentTimeMillis())+" remaining)" : ""));
                    if (iterations == 1) {
                        log.debug(msg);
                    } else {
                        log.trace(msg);
                    }
                }
            }

            if (iterationLimit > 0 && iterations == iterationLimit) {
                if (log.isDebugEnabled()) log.debug("{}: condition not satisfied and exceeded iteration limit", description);
                if (rethrowException && lastError != null) {
                    log.warn("{}: error caught checking condition (rethrowing): {}", description, lastError.getMessage());
                    throw Exceptions.propagate(lastError);
                }
				if (warnOnUnRethrownException && lastError != null)
					log.warn("{}: error caught checking condition: {}", description, lastError.getMessage());
                return false;
            }

            if (endTime > 0) {
				if (System.currentTimeMillis() > endTime) {
                    if (log.isDebugEnabled()) log.debug("{}: condition not satisfied and deadline {} passed", 
                            description, Time.makeTimeStringRounded(endTime - System.currentTimeMillis()));
	                if (rethrowException && lastError != null) {
	                    log.error("{}: error caught checking condition: {}", description, lastError.getMessage());
	                    throw Exceptions.propagate(lastError);
	                }
                    return false;
                }
            }

			Time.sleep(period);
        }
    }
}
