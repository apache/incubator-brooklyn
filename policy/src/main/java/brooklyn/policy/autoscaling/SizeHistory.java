package brooklyn.policy.autoscaling;

import java.util.List;

import brooklyn.util.MutableMap;
import brooklyn.util.TimeWindowedList;
import brooklyn.util.TimestampedValue;

import com.google.common.base.Objects;

public class SizeHistory {

    public static class WindowSummary {
        public final long latest;
        public final long min;
        public final long max;
        public final boolean stableForGrowth;
        public final boolean stableForShrinking;
        
        public WindowSummary(long latest, long min, long max, boolean stableForGrowth, boolean stableForShrinking) {
            this.latest = latest;
            this.min = min;
            this.max = max;
            this.stableForGrowth = stableForGrowth;
            this.stableForShrinking = stableForShrinking;
        }
        
        @Override
        public String toString() {
            return Objects.toStringHelper(this).add("latest", latest).add("min", min).add("max", max)
                    .add("stableForGrowth", stableForGrowth).add("stableForShrinking", stableForShrinking).toString();
        }
    }
    
    private final TimeWindowedList<Number> recentDesiredResizes;
    
    public SizeHistory(long windowSize) {
        recentDesiredResizes = new TimeWindowedList<Number>(MutableMap.of("timePeriod", windowSize, "minExpiredVals", 1));
    }

    public void add(final int val) {
        recentDesiredResizes.add(val);
    }

    /**
     * Summarises the history of values in this time window, with a few special things:
     * <ul>
     *   <li>If entire time-window is not covered by the given values, then min is Integer.MIN_VALUE and max is Integer.MAX_VALUE 
     *   <li>If no values, then latest is -1
     *   <li>If no recent values, then keeps last-seen value (no matter how old), to use that
     *   <li>"stable for growth" means that since that max value, there have not been any higher values
     *   <li>"stable for shrinking" means that since that low value, there have not been any lower values
     * </ul>
     */
    public WindowSummary summarizeWindow(long windowSize) {
        long now = System.currentTimeMillis();
        List<TimestampedValue<Number>> windowVals = recentDesiredResizes.getValuesInWindow(now, windowSize);
        
        Number latestObj = latestInWindow(windowVals);
        long latest = (latestObj == null) ? -1: latestObj.longValue();
        long max = maxInWindow(windowVals, windowSize).longValue();
        long min = minInWindow(windowVals, windowSize).longValue();
        boolean stable = (min == max);
        
        return new WindowSummary(latest, min, max, stable, stable);
    }
    
    /**
     * If the entire time-window is not covered by the given values, then returns Integer.MAX_VALUE.
     */
    private <T extends Number> T maxInWindow(List<TimestampedValue<T>> vals, long timewindow) {
        // TODO bad casting from Integer default result to T
        long now = System.currentTimeMillis();
        long epoch = now-timewindow;
        T result = null;
        double resultAsDouble = Integer.MAX_VALUE;
        for (TimestampedValue<T> val : vals) {
            T valAsNum = val.getValue();
            double valAsDouble = (valAsNum != null) ? valAsNum.doubleValue() : 0;
            if (result == null && val.getTimestamp() > epoch) {
                result = (T) Integer.valueOf(Integer.MAX_VALUE);
                resultAsDouble = result.doubleValue();
            }
            if (result == null || (valAsNum != null && valAsDouble > resultAsDouble)) {
                result = valAsNum;
                resultAsDouble = valAsDouble;
            }
        }
        return (T) (result != null ? result : Integer.MAX_VALUE);
    }
    
    /**
     * If the entire time-window is not covered by the given values, then returns Integer.MIN_VALUE
     */
    private <T extends Number> T minInWindow(List<TimestampedValue<T>> vals, long timewindow) {
        long now = System.currentTimeMillis();
        long epoch = now-timewindow;
        T result = null;
        double resultAsDouble = Integer.MIN_VALUE;
        for (TimestampedValue<T> val : vals) {
            T valAsNum = val.getValue();
            double valAsDouble = (valAsNum != null) ? valAsNum.doubleValue() : 0;
            if (result == null && val.getTimestamp() > epoch) {
                result = (T) Integer.valueOf(Integer.MIN_VALUE);
                resultAsDouble = result.doubleValue();
            }
            if (result == null || (val.getValue() != null && valAsDouble < resultAsDouble)) {
                result = valAsNum;
                resultAsDouble = valAsDouble;
            }
        }
        return (T) (result != null ? result : Integer.MIN_VALUE);
    }

    /**
     * @return null if empty, or the most recent value
     */
    private <T extends Number> T latestInWindow(List<TimestampedValue<T>> vals) {
        return vals.isEmpty() ? null : vals.get(vals.size()-1).getValue();
    }
}
