package brooklyn.util.internal

import groovy.time.Duration
import groovy.time.TimeDuration

import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.util.flags.FlagUtils
import brooklyn.util.flags.SetFromFlag

import com.google.common.base.Preconditions

/**
 * Simple DSL to repeat a fragment of code periodically until a condition is satisfied.
 *
 * In its simplest case, it is passed two closures - the first is executed, then the second. If the second closure returns false,
 * the loop is repeated; if true, it finishes. Further customization can be applied to set the period between loops and place a
 * maximum limit on how long the loop should run for.
 * <p>
 * It is configured in a <em>fluent</em> manner - for example:
 * <pre>
 * Repeater.create("Wait until the Frobnitzer is ready")
 *     .repeat {
 *         status = frobnitzer.getStatus()
 *     }
 *     .until {
 *         status == "Ready" || status == "Failed"
 *     }
 *     .limitIterationsTo(30)
 *     .run()
 * </pre>
 */
public class Repeater {
    private static final Logger log = LoggerFactory.getLogger(Repeater.class)

	@SetFromFlag
    private final String description
    private Callable body
    private Callable<Boolean> exitCondition
	@SetFromFlag
	private TimeDuration period = null
	@SetFromFlag("timeout")
	private TimeDuration durationLimit = null
    private int iterationLimit = 0
    private boolean rethrowException = false
    private boolean rethrowExceptionImmediately = false
	private boolean warnOnUnRethrownException = true

	public Repeater(Map flags=[:]) { this(flags, null) }
		
    /**
     * Construct a new instance of Repeater.
     *
     * @param flags: period, timeout, description
     * @param description a description of the operation that will appear in debug logs.
     */
    public Repeater(Map flags=[:], String description) {
    	setFromFlags(flags)
    	this.description = description ?: this.description ?: "Repeater"
    }

	public void setFromFlags(Map flags) {
		FlagUtils.setFieldsFromFlags(flags, this);
	}
	
	public static Repeater create(Map flags=[:]) {
		create(flags, null)
	}
    public static Repeater create(Map flags=[:], String description) {
        return new Repeater(flags, description)
    }

    /**
     * Set the main body of the loop.
     *
     * @param body a closure or other Runnable that is executed in the main body of the loop.
     * @return {@literal this} to aid coding in a fluent style.
     */
    Repeater repeat(Callable body={}) {
        Preconditions.checkNotNull body, "body must not be null"
        this.body = body
        return this
    }

	static { TimeExtras.init() }
	
    /**
     * Set how long to wait between loop iterations.
     *
     * @param period how long to wait between loop iterations.
     * @param unit the unit of measurement of the period.
     * @return {@literal this} to aid coding in a fluent style.
     */
    Repeater every(long period, TimeUnit unit) {
        Preconditions.checkArgument period > 0, "period must be positive: %s", period
        Preconditions.checkNotNull unit, "unit must not be null"
        this.period = period*unit
        return this
    }

    /**
     * @see #every(long, TimeUnit)
     */
    Repeater every(Duration duration) {
        Preconditions.checkNotNull duration, "duration must not be null"
		Preconditions.checkArgument duration.toMilliseconds()>0, "period must be positive: %s", duration
		this.period = duration
        return this
    }

    /**
     * @see #every(long, TimeUnit)
	 * @depreated specify unit
     */
    Repeater every(long duration) {
        return every(duration, TimeUnit.MILLISECONDS)
    }

    /**
     * Set code fragment that tests if the loop has completed.
     *
     * @param exitCondition a closure or other Callable that returns a boolean. If this code returns {@literal true} then the
     * loop will stop executing.
     * @return {@literal this} to aid coding in a fluent style.
     */
    Repeater until(Callable<Boolean> exitCondition) {
        Preconditions.checkNotNull exitCondition, "exitCondition must not be null"
        this.exitCondition = exitCondition
        return this
    }

    /**
     * If the exit condition check throws an exception, it will be recorded and the last exception will be thrown on failure.
     *
     * @return {@literal this} to aid coding in a fluent style.
     */
    Repeater rethrowException() {
        this.rethrowException = true
        return this
    }

   /**
    * If the repeated body or the exit condition check throws an exception, then propagate that exception immediately.
    *
    * @return {@literal this} to aid coding in a fluent style.
    */
   Repeater rethrowExceptionImmediately() {
       this.rethrowExceptionImmediately = true
       return this
   }

	Repeater suppressWarnings() {
		this.warnOnUnRethrownException = false
		return this
	}

    /**
     * Set the maximum number of iterations.
     *
     * The loop will exit if the condition has not been satisfied after this number of iterations.
     *
     * @param iterationLimit the maximum number of iterations.
     * @return {@literal this} to aid coding in a fluent style.
     */
    Repeater limitIterationsTo(int iterationLimit) {
        Preconditions.checkArgument iterationLimit > 0, "iterationLimit must be positive: %s", iterationLimit
        this.iterationLimit = iterationLimit
        return this
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
    Repeater limitTimeTo(long deadline, TimeUnit unit) {
        Preconditions.checkArgument deadline > 0, "deadline must be positive: %s", deadline
        Preconditions.checkNotNull unit, "unit must not be null"
        this.durationLimit = deadline * unit
        return this
    }

    /**
     * @see #limitTimeTo(long, TimeUnit)
     */
    Repeater limitTimeTo(Duration duration) {
        Preconditions.checkNotNull duration, "duration must not be null"
		Preconditions.checkArgument duration.toMilliseconds() > 0, "deadline must be positive: %s", duration
		this.durationLimit = duration
        return this
    }

    /**
     * @see #limitTimeTo(long, TimeUnit)
     * @deprecated will be deleted in 0.5.  specify unit
     */
    Repeater limitTimeTo(long duration) {
        return limitTimeTo(duration, TimeUnit.MILLISECONDS)
    }

    /**
     * Run the loop.
     *
     * @return true if the exit condition was satisfied; false if the loop terminated for any other reason.
     */
    boolean run() {
        Preconditions.checkState body != null, "repeat() method has not been called to set the body"
        Preconditions.checkState exitCondition != null, "until() method has not been called to set the exit condition"
        Preconditions.checkState period != null, "every() method has not been called to set the loop period time units"

        Throwable lastError = null
        int iterations = 0
		long endTime = -1
        if (durationLimit) {
			endTime = System.currentTimeMillis() + durationLimit.toMilliseconds()
        }

        while (true) {
            iterations++
            if (log.isDebugEnabled()) log.debug "{}: iteration {}", description, iterations

            try {
                body.call()
            } catch (Exception e) {
                log.warn description, e
                if (rethrowExceptionImmediately) throw e
            }

            boolean done = false
            try {
                lastError = null
                done = exitCondition.call()
            } catch (Exception e) {
                if (log.isDebugEnabled()) log.debug description, e
                lastError = e
                if (rethrowExceptionImmediately) throw e
            }
            if (done) {
                if (log.isDebugEnabled()) log.debug "{}: condition satisfied", description
                return true
            }

            if (iterationLimit > 0 && iterations == iterationLimit) {
                if (log.isDebugEnabled()) log.debug "{}: condition not satisfied and exceeded iteration limit", description
                if (rethrowException && lastError) {
                    log.warn("{}: error caught checking condition (rethrowing): {}", description, lastError.getMessage())
                    throw lastError
                }
				if (warnOnUnRethrownException && lastError)
					log.warn("{}: error caught checking condition: {}", description, lastError.getMessage())
                return false
            }

            if (endTime > 0) {
				if (System.currentTimeMillis() > endTime) {
                    if (log.isDebugEnabled()) log.debug "{}: condition not satisfied and deadline passed", description
	                if (rethrowException && lastError) {
	                    log.error("{}: error caught checking condition: {}", description, lastError.getMessage())
	                    throw lastError
	                }
                    return false
                }
            }

            try {
				Thread.sleep(period.toMilliseconds());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt()
            }
        }
    }
}
