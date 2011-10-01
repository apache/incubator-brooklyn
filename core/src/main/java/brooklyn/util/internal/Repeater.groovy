package brooklyn.util.internal

import groovy.time.Duration
import groovy.time.TimeDuration

import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

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

    private final String description
    private Closure body
    private Closure<Boolean> exitCondition
    private long period = 0L
    private TimeUnit periodUnit = null
    private int iterationLimit = 0
    private int deadline = 0
    private TimeUnit deadlineUnit = null
    private boolean rethrowException = false

    /**
     * Construct a new instance of Repeater.
     *
     * @param description a description of the operation that will appear in debug logs.
     */
    Repeater(String description) {
        this.description = description ?: "Repeater"
    }

    public static Repeater create(String description=null) {
        return new Repeater(description)
    }

    /**
     * Set the main body of the loop.
     *
     * @param body a closure or other Runnable that is executed in the main body of the loop.
     * @return {@literal this} to aid coding in a fluent style.
     */
    Repeater repeat(Closure body={}) {
        Preconditions.checkNotNull body, "body must not be null"
        this.body = body
        return this
    }

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
        this.period = period
        this.periodUnit = unit
        return this
    }

    /**
     * @see #every(long, TimeUnit)
     */
    Repeater every(Duration duration) {
        Preconditions.checkNotNull duration, "duration must not be null"
        return every(duration.toMilliseconds(), TimeUnit.MILLISECONDS)
    }

    /**
     * @see #every(long, TimeUnit)
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
    Repeater until(Closure<Boolean> exitCondition) {
        Preconditions.checkNotNull exitCondition, "exitCondition must not be null"
        this.exitCondition = exitCondition
        return this
    }

    /**
     * If the exit conditon check throws an exception, it will be recorded and the last exception will be thrown on failure.
     *
     * @return {@literal this} to aid coding in a fluent style.
     */
    Repeater rethrowException() {
        this.rethrowException = true
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
     * Set the maximum execution time.
     *
     * The loop will exit if the condition has not been satisfied after this deadline has elapsed.
     *
     * @param deadline the maximum time that the loop should run.
     * @param unit the unit of measurement of the period.
     * @return {@literal this} to aid coding in a fluent style.
     */
    Repeater limitTimeTo(long deadline, TimeUnit unit) {
        Preconditions.checkArgument deadline > 0, "deadline must be positive: %s", deadline
        Preconditions.checkNotNull unit, "unit must not be null"
        this.deadline = deadline
        this.deadlineUnit = unit
        return this
    }

    /**
     * @see #limitTimeTo(long, TimeUnit)
     */
    Repeater limitTimeTo(Duration duration) {
        Preconditions.checkNotNull duration, "duration must not be null"
        return limitTimeTo(duration.toMilliseconds(), TimeUnit.MILLISECONDS)
    }

    /**
     * @see #limitTimeTo(long, TimeUnit)
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
        Preconditions.checkState period > 0, "every() method has not been called to set the loop period"
        Preconditions.checkState periodUnit != null, "every() method has not been called to set the loop period time units"

        Throwable lastError = null
        int iterations = 0
        Calendar actualDeadline
        if (deadline > 0) {
            actualDeadline = Calendar.getInstance()
            switch (deadlineUnit) {
                case TimeUnit.DAYS: actualDeadline.add(Calendar.DATE, deadline); break
                case TimeUnit.HOURS: actualDeadline.add(Calendar.HOUR, deadline); break
                case TimeUnit.MINUTES: actualDeadline.add(Calendar.MINUTE, deadline); break
                case TimeUnit.SECONDS: actualDeadline.add(Calendar.SECOND, deadline); break
                case TimeUnit.MILLISECONDS: actualDeadline.add(Calendar.MILLISECOND, deadline); break
            }
        } else {
            actualDeadline = null
        }

        while (true) {
            iterations++
            log.debug "{}: iteration {}", description, iterations

            try {
                body.call()
            } catch (Exception e) {
                log.warn description, e
            }

            boolean done = false
            try {
                lastError = null
                done = exitCondition.call()
            } catch (Exception e) {
                log.debug description, e
                lastError = e
            }
            if (done) {
                log.debug "{}: condition satisfied", description
                return true
            }

            if (iterationLimit > 0 && iterations == iterationLimit) {
                log.debug "{}: condition not satisfied and exceeded iteration limit", description
                if (rethrowException && lastError) {
                    log.error("{}: error caught checking condition: {}", description, lastError.getMessage())
                    throw lastError
                }
                return false
            }

            if (actualDeadline != null) {
                Calendar now = Calendar.getInstance()
                if (now.after(actualDeadline)) {
                    log.debug "{}: condition not satisfied and deadline passed", description
	                if (rethrowException && lastError) {
	                    log.error("{}: error caught checking condition: {}", description, lastError.getMessage())
	                    throw lastError
	                }
                    return false
                }
            }

            try {
                periodUnit.sleep(period)
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt()
            }
        }
    }
}
