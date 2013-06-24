package brooklyn.event.feed;

import static com.google.common.base.Preconditions.checkNotNull;

import brooklyn.event.AttributeSensor;

import com.google.common.base.Function;
import com.google.common.base.Predicate;

import javax.annotation.Nullable;

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
    private Function<? super V, T> onfailure;
    private Function<? super Exception, T> onexception;
    private Predicate<? super V> checkSuccess;

    public FeedConfig(AttributeSensor<T> sensor) {
        this.sensor = checkNotNull(sensor, "sensor");
    }

    public FeedConfig(FeedConfig<V, T, This> other) {
        this.sensor = other.sensor;
        this.onsuccess = other.onsuccess;
        this.onfailure = other.onfailure;
        this.onexception = other.onexception;
        this.checkSuccess = other.checkSuccess;
    }

    @SuppressWarnings("unchecked")
    protected This self() {
        return (This) this;
    }
    
    public AttributeSensor<T> getSensor() {
        return sensor;
    }

    public Predicate<? super V> getCheckSuccess() {
        return checkSuccess;
    }
    
    public Function<? super V, T> getOnSuccess() {
        return onsuccess;
    }

    public Function<? super V, T> getOnFailure() {
        return onfailure;
    }
    
    /** @deprecated since 0.6; use {@link #getOnException()}) */
    public Function<? super Exception, T> getOnError() {
        return getOnException();
    }

    public Function<? super Exception, T> getOnException() {
        return onexception;
    }

    public This checkSuccess(Predicate<? super V> val) {
        this.checkSuccess = checkNotNull(val, "checkSuccess");
        return self();
    }

    public This onSuccess(Function<? super V,T> val) {
        this.onsuccess = checkNotNull(val, "onSuccess");
        return self();
    }
    
    public This onFailure(Function<? super V,T> val) {
        this.onfailure = checkNotNull(val, "onFailure");
        return self();
    }

    /** @deprecated since 0.6; use {@link #onException(Function) */
    public This onError(Function<? super Exception,T> val) {
        return onException(val);
    }

    public This onException(Function<? super Exception,T> val) {
        this.onexception = checkNotNull(val, "onException");
        return self();
    }

    public boolean hasSuccessHandler() {
        return this.onsuccess != null;
    }

    public boolean hasFailureHandler() {
        return this.onfailure != null;
    }

    public boolean hasExceptionHandler() {
        return this.onexception != null;
    }

    public boolean hasCheckSuccessHandler() {
        return this.checkSuccess != null;
    }
}
