package brooklyn.event.feed;

import static com.google.common.base.Preconditions.checkNotNull;
import brooklyn.event.AttributeSensor;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;

/**
 * Configuration for a poll, or a subscription etc, that is being added to a feed.
 * 
 * @author aled
 */
public class FeedConfig<V, T, F extends FeedConfig<V, T, F>> {

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

    public FeedConfig(FeedConfig<V, T, F> other) {
        this.sensor = other.sensor;
        this.onsuccess = other.onsuccess;
        this.onfailure = other.onfailure;
        this.onexception = other.onexception;
        this.checkSuccess = other.checkSuccess;
    }

    @SuppressWarnings("unchecked")
    protected F self() {
        return (F) this;
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
    
    public Function<? super Exception, T> getOnException() {
        return onexception;
    }

    /** sets the predicate used to check whether a feed run is successful */
    public F checkSuccess(Predicate<? super V> val) {
        this.checkSuccess = checkNotNull(val, "checkSuccess");
        return self();
    }

    public F onSuccess(Function<? super V,T> val) {
        this.onsuccess = checkNotNull(val, "onSuccess");
        return self();
    }
    
    public F setOnSuccess(T val) {
        return onSuccess(Functions.constant(val));
    }
    
    /** a failure is when the connection is fine (no exception) but the other end returns a result object V 
     * which the feed can tell indicates a failure (e.g. HTTP code 404) */
    public F onFailure(Function<? super V,T> val) {
        this.onfailure = checkNotNull(val, "onFailure");
        return self();
    }

    public F setOnFailure(T val) {
        return onFailure(Functions.constant(val));
    }

    /** registers a callback to be used {@link #onSuccess(Function)} and {@link #onFailure(Function)}, 
     * i.e. whenever a result comes back, but not in case of exceptions being thrown (ie problems communicating) */
    public F onResult(Function<? super V, T> val) {
        onSuccess(val);
        return onFailure(val);
    }

    public F setOnResult(T val) {
        return onResult(Functions.constant(val));
    }

    /** an exception is when there is an error in the communication */
    public F onException(Function<? super Exception,T> val) {
        this.onexception = checkNotNull(val, "onException");
        return self();
    }
    
    public F setOnException(T val) {
        return onException(Functions.constant(val));
    }

    /** convenience for indicating a behaviour to occur for both
     * {@link #onException(Function)}
     * (error connecting) and 
     * {@link #onFailure(Function)} 
     * (successful communication but failure report from remote end) */
    public F onFailureOrException(Function<Object,T> val) {
        onFailure(val);
        return onException(val);
    }
    
    public F setOnFailureOrException(T val) {
        return onFailureOrException(Functions.constant(val));
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
