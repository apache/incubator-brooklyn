package brooklyn.entity.group.zoneaware;

import brooklyn.location.Location;
import brooklyn.util.time.Duration;

import com.google.common.base.Predicate;

public class CriticalCauseZoneFailureDetector extends AbstractZoneFailureDetector {

    protected final long timeToConsider;
    protected final Predicate<? super Throwable> criticalityPredicate;
    private final int numTimes;
    
    /**
     * @param timeToConsider       Time for recent attempts (discard any attempts older than this)
     * @param criticalityPredicate What constitutes a critical cause
     * @param numTimes             Number of "critical causes" that must happen within the time period, to consider failed
     */
    public CriticalCauseZoneFailureDetector(Duration timeToConsider, Predicate<? super Throwable> criticalityPredicate, int numTimes) {
        this.timeToConsider = timeToConsider.toMilliseconds();
        this.criticalityPredicate = criticalityPredicate;
        this.numTimes = numTimes;
    }
    
    @Override
    protected boolean doHasFailed(Location loc, ZoneHistory zoneHistory) {
        synchronized (zoneHistory) {
            zoneHistory.trimOlderThan(System.currentTimeMillis() - timeToConsider);
            int count = 0;
            for (Throwable cause : zoneHistory.causes) {
                if (criticalityPredicate.apply(cause)) {
                    count++;
                }
            }
            return count >= numTimes;
        }
    }
}
