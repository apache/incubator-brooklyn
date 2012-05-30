package brooklyn.event.feed;

import static com.google.common.base.Preconditions.checkNotNull;

import brooklyn.event.AttributeSensor;

import com.google.common.base.Function;

/**
 * Configuration for a poll, or a subscription etc, that is being added to a feed.
 * 
 * @author aled
 */
public class FeedConfig<V, T, This extends FeedConfig<V,T,This>> {

    /** The onSuccess or onError functions can return this value to indicate that the sensor should not change. */
    public static final Object UNSET = new Object();
    
    private final AttributeSensor<T> sensor;
    private Function<? super V, T> onsuccess;
    private Function<? super Exception, T> onerror;

    public FeedConfig(AttributeSensor<T> sensor) {
        this.sensor = checkNotNull(sensor, "sensor");
    }

    public FeedConfig(FeedConfig<V, T, This> other) {
        this.sensor = other.sensor;
        this.onsuccess = other.onsuccess;
        this.onerror = other.onerror;
    }

    @SuppressWarnings("unchecked")
    protected This self() {
        return (This) this;
    }
    
    public AttributeSensor<T> getSensor() {
        return sensor;
    }
    
    public Function<? super V, T> getOnSuccess() {
        return onsuccess;
    }
    
    public Function<? super Exception, T> getOnError() {
        return onerror;
    }
    
    public This onSuccess(Function<? super V,T> val) {
        this.onsuccess = checkNotNull(val, "onSuccess");
        return self();
    }
    
    public This onError(Function<? super Exception,T> val) {
        this.onerror = checkNotNull(val, "onError");
        return self();
    }
}
