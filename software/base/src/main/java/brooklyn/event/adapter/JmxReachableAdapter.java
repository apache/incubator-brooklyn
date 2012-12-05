package brooklyn.event.adapter;

import groovy.lang.Closure;

import java.util.Map;

import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.MutableMap;

import com.google.common.base.Function;


/** 
 * Adapter that polls for a JMX attribute.
 * "Attribute" here refers to the JMX concept, rather than the brooklyn concept.
 * 
 * @see {@link JmxSensorAdapter} for recommended way of using this
 */
public class JmxReachableAdapter extends AbstractSensorAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(JmxReachableAdapter.class);
    
	final JmxSensorAdapter adapter;
	final ObjectName objectName;
	final ReachablePollHelper poller;

	public JmxReachableAdapter(JmxSensorAdapter adapter, ObjectName objectName) {
	    this(MutableMap.of(), adapter, objectName);
	}
	public JmxReachableAdapter(Map flags, JmxSensorAdapter adapter, ObjectName objectName) {
		super(flags);
		this.adapter = adapter;
// FIXME        adapter.addActivationLifecycleListeners(
//                new Runnable() { public void run() { activateAdapter(); } },
//                new Runnable() { public void run() { deactivateAdapter(); } });
		poller = new ReachablePollHelper(adapter, objectName);
		poller.init();
		this.objectName = objectName;
	}
	
	static class ReachablePollHelper extends AbstractPollHelper {
		JmxSensorAdapter adapter;
		ObjectName objectName;
		ReachablePollHelper(JmxSensorAdapter adapter, ObjectName objectName) {
			super(adapter);
			this.adapter = adapter;
			this.objectName = objectName;
		}
		@Override
		protected AbstractSensorEvaluationContext executePollOnSuccess() {
		    SingleValueResponseContext result = new SingleValueResponseContext();
		    try {
		        result.setValue(adapter.getHelper().findMBean(objectName) != null);
		    } catch (Exception e) {
		        if (LOG.isDebugEnabled()) LOG.debug("Error for "+getEntity()+" "+adapter.getHelper().getUrl()+
		                ", finding MBean "+objectName+"; assuming unreachable", e);
		        result.setValue(false);
		    }
		    return result;
		}
	}
	
	public void poll(Closure listener) {
		poller.addListener(listener);
	}
	
    public void poll(Function<Boolean, Void> listener) {
        poller.addListener(GroovyJavaMethods.closureFromFunction(listener));
    }
    
    @Override
    protected void activateAdapter() {
        super.activateAdapter();
    }
}
