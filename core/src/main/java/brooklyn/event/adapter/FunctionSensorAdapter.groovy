package brooklyn.event.adapter;

import groovy.lang.Closure

import java.util.Map
import java.util.concurrent.Callable

import brooklyn.event.Sensor

import com.google.common.base.Function


/**
 * Entry point for wiring up arbitrary functions to be used as the source
 * for sensors.
 * <p>
 * Example usage:
 * <code>
 *   def fnSensorAdaptor = new FunctionSensorAdapter(&myFunction);
 *   fnSensorAdaptor.poll(MY_BROOKLYN_RAW_DATA_ATTRIBTE)
 *   fnSensorAdaptor.then({ MyStruct.parse(it) }).with {
 *       then({ it.field1 }).poll(MY_BROOKLYN_ATTRIBUTE_1)
 *       then({ it.field2 }).poll(MY_BROOKLYN_ATTRIBUTE_2)
 *   }
 *   //or, field1 access can also be written:
 *   fnSensorAdaptor.poll(MY_BROOKLYN_ATTRIBTE_1, { MyStruct.parse(it).field1 } )
 * </code>
 */
public class FunctionSensorAdapter extends AbstractSensorAdapter {

	public static final long CALL_TIMEOUT_MS = 120*1000;

    protected final Callable callable;
    protected final FunctionBasePollHelper poller = new FunctionBasePollHelper(this);
    
    public FunctionSensorAdapter(Map flags=[:]) {
        this(flags, {null});
    }
	public FunctionSensorAdapter(Map flags=[:], Callable c) {
		super(flags)
        this.callable = c;
	}

    public void poll(Sensor s, Closure c={it}) {
        poller.addSensor(s, c);
    }

    public Object call() {
        callable.call()
    }
    
	public FunctionCallAdapter then(Closure f) { return thenF(f as Function); }
	public FunctionCallAdapter then(Function f) { return thenF(f); }
    
	private FunctionCallAdapter thenF(Function f) { return registry.register(new FunctionCallAdapter(poller, f)); }
    
}

public class FunctionBasePollHelper extends AbstractChainablePollHelper {
    public FunctionBasePollHelper(FunctionSensorAdapter adapter) {
        super(adapter);
    }
    @Override
    protected AbstractSensorEvaluationContext executePollOnSuccess() {
        def result = ((FunctionSensorAdapter)adapter).call();
        return new SingleValueResponseContext(value: result);
    }
}

public class FunctionChainPollHelper extends AbstractChainablePollHelper {
    public FunctionChainPollHelper(FunctionCallAdapter adapter) {
        super(adapter);
    }
    @Override
    void evaluateSensorsOnResponse(AbstractSensorEvaluationContext response) {
        def result = ((FunctionCallAdapter)adapter).function.apply(response.defaultValue);
        def response2 = new SingleValueResponseContext(value: result);
        super.evaluateSensorsOnResponse(response2)
    }
    // this doesn't have a poller, he is just a child
    // TODO move methods below to a AbstractChainableNonPollingExtensionPollHelper
    // (or refactor hierarchy of poller to pull polling logic down?)
    @Override protected activatePoll() {}
    @Override protected deactivatePoll() {}
    @Override
    protected AbstractSensorEvaluationContext executePollOnSuccess() {
        //this chain poller is not executed, it is triggered by the parent (the adapters parent)
        throw new IllegalStateException("poll execution not relevant for chained function");
    }
}

public class FunctionCallAdapter extends AbstractSensorAdapter {
 
    protected final FunctionChainPollHelper poller;
    protected final Function function;
    protected final AbstractSensorAdapter parent;
    
    public FunctionCallAdapter(Map flags=[:], AbstractChainablePollHelper parentPoller, Function f) {
        super(flags);
        this.parent = parentPoller.adapter;
        this.function = f;
        this.poller = new FunctionChainPollHelper(this);
        parentPoller.addSubPoller(this.poller);
    }

    public void poll(Sensor s, Closure c={it}) {
        poller.addSensor(s, c);
    }

    public FunctionCallAdapter then(Closure f) { return thenF(f as Function); }
    public FunctionCallAdapter then(Function f) { return thenF(f); }
    
    private FunctionCallAdapter thenF(Function f) { return registry.register(new FunctionCallAdapter(poller, f)); }
    
}
