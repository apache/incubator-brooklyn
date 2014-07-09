package brooklyn.entity.group.zoneaware;

import brooklyn.location.Location;
import brooklyn.util.time.Duration;

import com.google.common.base.Ticker;

public class ProportionalZoneFailureDetector extends AbstractZoneFailureDetector {

    // TODO Would be nice to weight it disproportionately for more recent attempts; but this will do for now.
    
    protected final int minDatapoints;
    protected final long timeToConsider;
    protected final double maxProportionFailures;
    
    /**
     * @param minDatapoints         min number of attempts within the time period, to consider this measure reliable
     * @param timeToConsider        time for recent attempts (discard any attempts older than this)
     * @param maxProportionFailures proportion (between 0 and 1) where numFailures/dataPoints >= this number means failure
     */
    public ProportionalZoneFailureDetector(int minDatapoints, Duration timeToConsider, double maxProportionFailures) {
        this(minDatapoints, timeToConsider, maxProportionFailures, Ticker.systemTicker());
    }
    
    public ProportionalZoneFailureDetector(int minDatapoints, Duration timeToConsider, double maxProportionFailures, Ticker ticker) {
        super(ticker);
        this.minDatapoints = minDatapoints;
        this.timeToConsider = timeToConsider.toMilliseconds();
        this.maxProportionFailures = maxProportionFailures;
    }
    
    @Override
    protected boolean doHasFailed(Location loc, ZoneHistory zoneHistory) {
        synchronized (zoneHistory) {
            zoneHistory.trimOlderThan(currentTimeMillis() - timeToConsider);
            int numDatapoints = zoneHistory.successes.size() + zoneHistory.failures.size();
            double proportionFailure = ((double)zoneHistory.failures.size()) / ((double)numDatapoints);
            return numDatapoints >= minDatapoints && proportionFailure >= maxProportionFailures;
        }
    }
}
