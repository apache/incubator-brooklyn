package brooklyn.enricher.basic

import java.util.Map;

import com.google.common.base.Preconditions

import brooklyn.policy.Enricher;
import brooklyn.policy.basic.AbstractEntityAdjunct;
import brooklyn.util.flags.FlagUtils
import brooklyn.util.internal.LanguageUtils

/**
* Base {@link Enricher} implementation; all enrichers should extend this or its children
*/
public abstract class AbstractEnricher extends AbstractEntityAdjunct implements Enricher {

    protected Map leftoverProperties = [:]
    
    public AbstractEnricher(Map flags = [:]) {
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
    

}
