package brooklyn.event.adapter

import static com.google.common.base.Preconditions.checkNotNull
import static java.util.concurrent.TimeUnit.*
import groovy.time.TimeDuration

import java.util.concurrent.CopyOnWriteArrayList

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal
import brooklyn.util.flags.FlagUtils
import brooklyn.util.flags.SetFromFlag
import brooklyn.util.internal.TimeExtras
import brooklyn.util.task.ParallelTask

/** 
 * Captures common fields and processes for sensor adapters
 * 
 * @deprecated See brooklyn.event.feed.*
 */
@Deprecated
public abstract class AbstractSensorAdapter {

	private static final Logger log = LoggerFactory.getLogger(AbstractSensorAdapter)
	
	static { TimeExtras.init() }
	
    /** null means no execution; 0 or negative is undefined (but currently no execution) */
	@SetFromFlag("period")
	TimeDuration pollPeriod = 5*SECONDS;
	
	SensorRegistry registry;
	EntityLocal entity;
	boolean activated;
	
	public AbstractSensorAdapter(Map flags=[:]) {
		Map unused = FlagUtils.setFieldsFromFlags(flags, this);
		if (unused) log.warn("unknown flags when constructing {}: {}", this, unused)
	}

	void register(SensorRegistry registry) {
		if (this.registry==registry) return
		if (this.registry!=null) throw new IllegalStateException("cannot change registry for ${this}: from ${this.registry} to ${registry}")
		if (!registry.adapters.contains(this)) 
			throw new IllegalStateException("${this}.register should only be called by ${registry}");
		this.registry = checkNotNull(registry, "registry");
		this.entity = checkNotNull(registry.entity, "entity");
		registry.addActivationLifecycleListeners({ activateAdapter() }, { deactivateAdapter() })
	}
	
	private final List<Runnable> activationListeners = new CopyOnWriteArrayList<Runnable>();
	private final List<Runnable> deactivationListeners = new CopyOnWriteArrayList<Runnable>();
    
    /**
     * TODO If called in separate thread concurrently with register() then listener could be called twice.
     * Recommend not adding activation listeners like that!
     */
	protected void addActivationLifecycleListeners(Runnable onUp, Runnable onDown) {
		activationListeners << checkNotNull(onUp, "onUp");
		deactivationListeners << checkNotNull(onDown, "onDown");
        
        if (activated) {
            onUp.call();
        } else {
            onDown.call();
        }
	}
    
	protected void activateAdapter() {
        if (activated) return; //prevent double activation
		if (log.isDebugEnabled()) log.debug "activating adapter {} for {}", this, entity
        entity.getExecutionContext().submit(new ParallelTask(activationListeners)).get();
		activated = true;
	}
    
	protected void deactivateAdapter() {
		if (log.isDebugEnabled()) log.debug "deactivating adapter {} for {}", this, entity
		activated = false;
		deactivationListeners.each { it.run() }
	}

	protected boolean isConnected() { isActivated() }
	
}
