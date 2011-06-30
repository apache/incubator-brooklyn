package brooklyn.util.internal

import org.testng.annotations.Test
import static org.testng.Assert.*
import java.util.concurrent.TimeUnit

class RepeaterTest {

    @Test
    public void sanityTest() {
        new Repeater("Sanity test").repeat({}).until({true}).every(10, TimeUnit.MILLISECONDS);
    }

    @Test
    public void repeatFailsIfClosureIsNull() {
        try {
            new Repeater("repeatFailsIfClosureIsNull").repeat(null);
        } catch(NullPointerException e) {
            return
        }
        fail "Expected exception was not thrown"
    }

    @Test
    public void repeatSucceedsIfClosureIsNonNull() {
        new Repeater("repeatSucceedsIfClosureIsNonNull").repeat({});
    }

    @Test
    public void untilFailsIfClosureIsNull() {
        try {
            new Repeater("untilFailsIfClosureIsNull").until(null);
        } catch(NullPointerException e) {
            return
        }
        fail "Expected exception was not thrown"
    }

    @Test
    public void untilSucceedsIfClosureIsNonNull() {
        new Repeater("untilSucceedsIfClosureIsNonNull").until({true});
    }

    @Test
    public void everyFailsIfPeriodIsZero() {
        try {
            new Repeater("everyFailsIfPeriodIsZero").every(0, TimeUnit.MILLISECONDS);
        } catch(IllegalArgumentException e) {
            return
        }
        fail "Expected exception was not thrown"
    }

    @Test
    public void everyFailsIfPeriodIsNegative() {
        try {
            new Repeater("everyFailsIfPeriodIsNegative").every(-1, TimeUnit.MILLISECONDS);
        } catch(IllegalArgumentException e) {
            return
        }
        fail "Expected exception was not thrown"
    }

    @Test
    public void everyFailsIfUnitsIsNull() {
        try {
            new Repeater("everyFailsIfUnitsIsNull").every(10, null);
        } catch(NullPointerException e) {
            return
        }
        fail "Expected exception was not thrown"
    }

    @Test
    public void everySucceedsIfPeriodIsPositiveAndUnitsIsNonNull() {
        new Repeater("repeatSucceedsIfClosureIsNonNull").every(10, TimeUnit.MILLISECONDS);
    }

    @Test
    public void limitTimeToFailsIfPeriodIsZero() {
        try {
            new Repeater("limitTimeToFailsIfPeriodIsZero").limitTimeTo(0, TimeUnit.MILLISECONDS);
        } catch(IllegalArgumentException e) {
            return
        }
        fail "Expected exception was not thrown"
    }

    @Test
    public void limitTimeToFailsIfPeriodIsNegative() {
        try {
            new Repeater("limitTimeToFailsIfPeriodIsNegative").limitTimeTo(-1, TimeUnit.MILLISECONDS);
        } catch(IllegalArgumentException e) {
            return
        }
        fail "Expected exception was not thrown"
    }

    @Test
    public void limitTimeToFailsIfUnitsIsNull() {
        try {
            new Repeater("limitTimeToFailsIfUnitsIsNull").limitTimeTo(10, null);
        } catch(NullPointerException e) {
            return
        }
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

    @Test
    public void runFailsIfBodyWasNotSet() {
        try {
            new Repeater("runFailsIfBodyWasNotSet").every(10, TimeUnit.MILLISECONDS).until({true}).run();
        } catch(IllegalStateException e) {
            return;
        }
        fail "Expected exception was not thrown"
    }

    @Test
    public void runFailsIfUntilWasNotSet() {
        try {
            new Repeater("runFailsIfUntilWasNotSet").repeat({}).every(10, TimeUnit.MILLISECONDS).run();
        } catch(IllegalStateException e) {
            return;
        }
        fail "Expected exception was not thrown"
    }

    @Test
    public void runFailsIfEveryWasNotSet() {
        try {
            new Repeater("runFailsIfEveryWasNotSet").repeat({}).until({true}).run();
        } catch(IllegalStateException e) {
            return;
        }
        fail "Expected exception was not thrown"
    }
}
