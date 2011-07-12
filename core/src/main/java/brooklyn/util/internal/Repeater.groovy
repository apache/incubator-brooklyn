package brooklyn.util.internal

import groovy.time.Duration

import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.base.Preconditions

/**
 * Repeat a fragment of code periodically until a condition is satisfied. In its simplest case, it is passed two closures -
 * the first is executed, then the second. If the second closure returns false, the loop is repeated; if true, it finishes.
 * Further customization can be applied to set the period between loops and place a maximum limit on how long the loop should
 * run for.
 *
 * It is configured in a "fluent" manner - for example:
 *
 * <code>
 * new Repeater("Wait until the Frobnitzer is ready")
 * .repeat( { try { status = frobnitzer.getStatus() } catch(Exception e) { status = "Failed" } } )
 * .until( { status == "Ready" || status == "Failed" } )
 * .limitIterationsTo(30)
 * .run()
 * </code>
 */
class Repeater {
    private static final Logger log = LoggerFactory.getLogger(Repeater.class)

    private final String description
    private Runnable body
    private Callable<Boolean> exitCondition
    private int period = 0;
    private TimeUnit periodUnit = null;
    private int iterationLimit = 0;
    private int deadline = 0;
    private TimeUnit deadlineUnit = null;

    /**
     * Construct a new instance of Repeater.
     * @param description a description of the operation that will appear in debug logs.
     */
    Repeater(String description) {
        this.description = description;
    }

    /**
     * Set the main body of the loop.
     * @param body a closure or other Runnable that is executed in the main body of the loop.
     * @return <code>this<code> (to aid coding in a fluent style)
     */
    Repeater repeat(Runnable body={}) {
        Preconditions.checkNotNull body, "body must not be null"
        this.body = body;
        return this;
    }

    /**
     * Set how long to wait between loop iterations.
     * @param period how long to wait between loop iterations
     * @param unit the unit of measurement of the period
     * @return <code>this<code> (to aid coding in a fluent style)
     */
    Repeater every(int period, TimeUnit unit) {
        Preconditions.checkArgument period > 0, "period must be positive: %s", period
        Preconditions.checkNotNull unit, "unit must not be null"
        this.period = period;
        this.periodUnit = unit;
        return this;
    }

    /**
     * Set code fragment that tests if the loop has completed.
     * @param exitCondition a closure or other Callable that returns a boolean. If this code returns <code>true<code>, then the
     * loop will stop executing.
     * @return <code>this<code> (to aid coding in a fluent style)
     */
    Repeater until(Callable<Boolean> exitCondition) {
        Preconditions.checkNotNull exitCondition, "exitCondition must not be null"
        this.exitCondition = exitCondition;
        return this;
    }

    /**
     * Set the maximum number of iterations. The loop will exit if the condition has not been satisfied after this number of
     * iterations.
     * @param iterationLimit the maximum number of iterations.
     * @return <code>this<code> (to aid coding in a fluent style)
     */
    Repeater limitIterationsTo(int iterationLimit) {
        Preconditions.checkArgument iterationLimit > 0, "iterationLimit must be positive: %s", iterationLimit
        this.iterationLimit = iterationLimit;
        return this;
    }

    /**
     * Set the maximum execution time. The loop will exit if the condition has not been satisfied after this deadline has
     * elapsed.
     * @param deadline the maximum time that the loop should run
     * @param unit the unit of measurement of the period
     * @return <code>this<code> (to aid coding in a fluent style)
     */
    Repeater limitTimeTo(int deadline, TimeUnit unit) {
        Preconditions.checkArgument deadline > 0, "deadline must be positive: %s", deadline
        Preconditions.checkNotNull unit, "unit must not be null"
        this.deadline = deadline;
        this.deadlineUnit = unit;
        return this;
    }

    Repeater limitTimeTo(Duration duration) {
        Preconditions.checkArgument duration > 0, "duration must be positive: %s", duration
        return limitTimeTo(duration.toMilliseconds(), TimeUnit.MILLISECONDS)
    }

    /**
     * Run the loop.
     * @return true if the exit condition was satisfied; false if the loop terminated for any other reason.
     */
    boolean run() {
        Preconditions.checkState body != null, "repeat() method has not been called to set the body"
        Preconditions.checkState exitCondition != null, "until() method has not been called to set the exit condition"
        Preconditions.checkState period > 0, "every() method has not been called to set the loop period"
        Preconditions.checkState periodUnit != null, "every() method has not been called to set the loop period time units"

        int iterations = 0;
        Calendar actualDeadline;
        if (deadline > 0) {
            actualDeadline = Calendar.getInstance();
            switch(deadlineUnit) {
                case TimeUnit.DAYS: actualDeadline.add(Calendar.DATE, deadline); break;
                case TimeUnit.HOURS: actualDeadline.add(Calendar.HOUR, deadline); break;
                case TimeUnit.MINUTES: actualDeadline.add(Calendar.MINUTE, deadline); break;
                case TimeUnit.SECONDS: actualDeadline.add(Calendar.SECOND, deadline); break;
                case TimeUnit.MILLISECONDS: actualDeadline.add(Calendar.MILLISECOND, deadline); break;
            }
        } else {
            actualDeadline = null;
        }

        for(;;) {
            iterations++;
            log.debug "{}: iteration {}", description, iterations

            try {
                body.call();
            } catch(Exception e) {
                log.warn description, e
            }

            boolean done = exitCondition.call();
            if (done) {
                log.debug "{}: condition satisfied", description
                return true
            };

            if (iterationLimit > 0 && iterations == iterationLimit) {
                log.debug "{}: condition not satisfied and exceeded iteration limit", description
                return false
            };

            if (actualDeadline != null) {
                Calendar now = Calendar.getInstance();
                if (now.after(actualDeadline)) {
                    log.debug "{}: condition not satisfied and deadline passed", description
                    return false
                };
            }

            periodUnit.sleep(period);
        }
    }
}
