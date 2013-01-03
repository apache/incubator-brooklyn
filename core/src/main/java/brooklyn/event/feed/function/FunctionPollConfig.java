package brooklyn.event.feed.function;

import static com.google.common.base.Preconditions.checkNotNull;
import groovy.lang.Closure;

import java.util.concurrent.Callable;

import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.PollConfig;
import brooklyn.util.GroovyJavaMethods;

public class FunctionPollConfig<S, T> extends PollConfig<S, T, FunctionPollConfig<S, T>> {

    private Callable<?> callable;
    
    public FunctionPollConfig(AttributeSensor<T> sensor) {
        super(sensor);
    }

    public FunctionPollConfig(FunctionPollConfig<S, T> other) {
        super(other);
        callable = other.callable;
    }
    
    public Callable<? extends Object> getCallable() {
        return callable;
    }
    
    public FunctionPollConfig<S, T> callable(Callable<? extends S> val) {
        this.callable = checkNotNull(val, "callable");
        return this;
    }
    
    public FunctionPollConfig<S, T> closure(Closure<?> val) {
        this.callable = GroovyJavaMethods.callableFromClosure(checkNotNull(val, "closure"));
        return this;
    }
}
