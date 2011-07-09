package brooklyn.util.internal

import org.testng.annotations.Test
import static org.testng.Assert.*
import java.util.concurrent.TimeUnit

class RepeaterTest {

    @Test
    public void sanityTest() {
        new Repeater("Sanity test").repeat({}).until({true}).every(10, TimeUnit.MILLISECONDS);
    }

    @Test(expectedExceptions = [ NullPointerException.class ])
    public void repeatFailsIfClosureIsNull() {
        new Repeater("repeatFailsIfClosureIsNull").repeat(null);
        fail "Expected exception was not thrown"
    }

    @Test
    public void repeatSucceedsIfClosureIsNonNull() {
        new Repeater("repeatSucceedsIfClosureIsNonNull").repeat({});
    }

    @Test(expectedExceptions = [ NullPointerException.class ])
    public void untilFailsIfClosureIsNull() {
        new Repeater("untilFailsIfClosureIsNull").until(null);
        fail "Expected exception was not thrown"
    }

    @Test
    public void untilSucceedsIfClosureIsNonNull() {
        new Repeater("untilSucceedsIfClosureIsNonNull").until({true});
    }

    @Test(expectedExceptions = [ IllegalArgumentException.class ])
    public void everyFailsIfPeriodIsZero() {
        new Repeater("everyFailsIfPeriodIsZero").every(0, TimeUnit.MILLISECONDS);
        fail "Expected exception was not thrown"
    }

    @Test(expectedExceptions = [ IllegalArgumentException.class ])
    public void everyFailsIfPeriodIsNegative() {
        new Repeater("everyFailsIfPeriodIsNegative").every(-1, TimeUnit.MILLISECONDS);
        fail "Expected exception was not thrown"
    }

    @Test(expectedExceptions = [ NullPointerException.class ])
    public void everyFailsIfUnitsIsNull() {
        new Repeater("everyFailsIfUnitsIsNull").every(10, null);
        fail "Expected exception was not thrown"
    }

    @Test
    public void everySucceedsIfPeriodIsPositiveAndUnitsIsNonNull() {
        new Repeater("repeatSucceedsIfClosureIsNonNull").every(10, TimeUnit.MILLISECONDS);
    }

    @Test(expectedExceptions = [ IllegalArgumentException.class ])
    public void limitTimeToFailsIfPeriodIsZero() {
        new Repeater("limitTimeToFailsIfPeriodIsZero").limitTimeTo(0, TimeUnit.MILLISECONDS);
        fail "Expected exception was not thrown"
    }

    @Test(expectedExceptions = [ IllegalArgumentException.class ])
    public void limitTimeToFailsIfPeriodIsNegative() {
        new Repeater("limitTimeToFailsIfPeriodIsNegative").limitTimeTo(-1, TimeUnit.MILLISECONDS);
        fail "Expected exception was not thrown"
    }

    @Test(expectedExceptions = [ NullPointerException.class ])
    public void limitTimeToFailsIfUnitsIsNull() {
        new Repeater("limitTimeToFailsIfUnitsIsNull").limitTimeTo(10, null);
        fail "Expected exception was not thrown"
    }

    @Test
    public void limitTimeToSucceedsIfPeriodIsPositiveAndUnitsIsNonNull() {
        new Repeater("limitTimeToSucceedsIfClosureIsNonNull").limitTimeTo(10, TimeUnit.MILLISECONDS);
    }

    @Test
    public void runReturnsTrueIfExitConditionIsTrue() {
        assertTrue new Repeater("runReturnsTrueIfExitConditionIsTrue")
            .repeat({}).every(1, TimeUnit.MILLISECONDS).until({true}).run();
    }

    @Test
    public void runRespectsMaximumIterationLimitAndReturnsFalseIfReached() {
        int iterations = 0;
        assertFalse new Repeater("runRespectsMaximumIterationLimitAndReturnsFalseIfReached")
            .repeat({iterations++}).every(1, TimeUnit.MILLISECONDS).until({false}).limitIterationsTo(5).run();
        assertEquals 5, iterations;
    }

    @Test
    public void runRespectsTimeLimitAndReturnsFalseIfReached() {
        final int DEADLINE = 200;
        Repeater repeater = new Repeater("runRespectsTimeLimitAndReturnsFalseIfReached")
            .repeat({}).every(10, TimeUnit.MILLISECONDS).until({false}).limitTimeTo(DEADLINE, TimeUnit.MILLISECONDS);

        Calendar start = Calendar.getInstance();
        boolean result = repeater.run();
        Calendar end = Calendar.getInstance();

        assertFalse result;

        long difference = end.timeInMillis - start.timeInMillis
        assertTrue difference > DEADLINE*0.9
        assertTrue difference < DEADLINE*1.1
    }

    @Test(expectedExceptions = [ IllegalStateException.class ])
    public void runFailsIfBodyWasNotSet() {
        new Repeater("runFailsIfBodyWasNotSet").every(10, TimeUnit.MILLISECONDS).until({true}).run();
        fail "Expected exception was not thrown"
    }

    @Test(expectedExceptions = [ IllegalStateException.class ])
    public void runFailsIfUntilWasNotSet() {
        new Repeater("runFailsIfUntilWasNotSet").repeat({}).every(10, TimeUnit.MILLISECONDS).run();
        fail "Expected exception was not thrown"
    }

    @Test(expectedExceptions = [ IllegalStateException.class ])
    public void runFailsIfEveryWasNotSet() {
        new Repeater("runFailsIfEveryWasNotSet").repeat({}).until({true}).run();
        fail "Expected exception was not thrown"
    }
}
