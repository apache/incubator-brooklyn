package brooklyn.event.feed;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.concurrent.TimeUnit;

import brooklyn.event.AttributeSensor;
import brooklyn.util.time.Duration;

/**
 * Configuration for polling, which is being added to a feed (e.g. to poll a given URL over http).
 * 
 * @author aled
 */
public class PollConfig<V, T, This extends PollConfig<V,T,This>> extends FeedConfig<V,T,This> {

    private long period = -1;

    public PollConfig(AttributeSensor<T> sensor) {
        super(sensor);
    }

    public PollConfig(PollConfig<V,T,This> other) {
        super(other);
        this.period = other.period;
    }

    public long getPeriod() {
        return period;
    }
    
    public This period(Duration val) {
        checkArgument(val.toMilliseconds() >= 0, "period must be greater than or equal to zero");
        this.period = val.toMilliseconds();
        return self();
    }
    
    public This period(long val) {
        checkArgument(val >= 0, "period must be greater than or equal to zero");
        this.period = val; return self();
    }
    
    public This period(long val, TimeUnit units) {
        checkArgument(val >= 0, "period must be greater than or equal to zero");
        return period(units.toMillis(val));
    }
}
