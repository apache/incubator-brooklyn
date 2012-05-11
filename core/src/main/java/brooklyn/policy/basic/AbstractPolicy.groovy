package brooklyn.policy.basic

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.management.ExecutionContext
import brooklyn.policy.Policy
import brooklyn.util.flags.FlagUtils
import brooklyn.util.internal.LanguageUtils

import com.google.common.base.Preconditions

/**
 * Base {@link Policy} implementation; all policies should extend this or its children
 */
public abstract class AbstractPolicy extends AbstractEntityAdjunct implements Policy {
    private static final Logger log = LoggerFactory.getLogger(AbstractPolicy.class)

    protected String policyStatus
    protected Map leftoverProperties = [:]
    protected AtomicBoolean suspended = new AtomicBoolean(false)

    protected transient ExecutionContext execution

    public AbstractPolicy(Map flags = [:]) {
        configure(flags)
        FlagUtils.checkRequiredFields(this)
    }

    /** will set fields from flags, and put the remaining ones into the 'leftovers' map.
     * can be subclassed for custom initialization but note the following. 
     * <p>
     * if you require fields to be initialized you must do that in this method. You must
     * *not* rely on field initializers because they may not run until *after* this method
     * (this method is invoked by the constructor in this class, so initializers
     * in subclasses will not have run when this overridden method is invoked.) */ 
    protected void configure(Map properties=[:]) {
        leftoverProperties << FlagUtils.setFieldsFromFlags(properties, this)
        //replace properties _contents_ with leftovers so subclasses see leftovers only
        properties.clear();
        properties.putAll(leftoverProperties)
        leftoverProperties = properties;
        
        if (id==null) id = LanguageUtils.newUid();
        
        if (!name && properties.displayName) {
            //'displayName' is a legacy way to refer to a location's name
            //FIXME could this be a GString?
            Preconditions.checkArgument properties.displayName instanceof CharSequence, "'displayName' property should be a string"
            name = properties.remove("displayName")
        }
    }
    
    public void suspend() {
        suspended.set(true)
    }

    public void resume() {
        suspended.set(false)
    }

    public boolean isSuspended() {
        return suspended.get().booleanValue()
    }

    @Override
    public void destroy(){
        suspend()
        super.destroy()
    }

    @Override
    public boolean isRunning() {
        return !isSuspended() && !isDestroyed()
    }
}
