package brooklyn.event.feed;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.concurrent.TimeUnit;

import brooklyn.event.AttributeSensor;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.time.Duration;

/**
 * Configuration for polling, which is being added to a feed (e.g. to poll a given URL over http).
 * 
 * @author aled
 */
public class PollConfig<V, T, F extends PollConfig<V, T, F>> extends FeedConfig<V, T, F> {

    private long period = -1;
    private String description;

    public PollConfig(AttributeSensor<T> sensor) {
        super(sensor);
    }

    public PollConfig(PollConfig<V,T,F> other) {
        super(other);
        this.period = other.period;
    }

    public long getPeriod() {
        return period;
    }
    
    public F period(Duration val) {
        checkArgument(val.toMilliseconds() >= 0, "period must be greater than or equal to zero");
        this.period = val.toMilliseconds();
        return self();
    }
    
    public F period(long val) {
        checkArgument(val >= 0, "period must be greater than or equal to zero");
        this.period = val; return self();
    }
    
    public F period(long val, TimeUnit units) {
        checkArgument(val >= 0, "period must be greater than or equal to zero");
        return period(units.toMillis(val));
    }
    
    public F description(String description) {
        this.description = description;
        return self();
    }
    
    @Override
    public String toString() {
        if (description!=null) return description;
        return JavaClassNames.simpleClassName(this);
    }

}
