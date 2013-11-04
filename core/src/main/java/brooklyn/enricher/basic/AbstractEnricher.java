package brooklyn.enricher.basic;

import java.util.Map;

import brooklyn.policy.Enricher;
import brooklyn.policy.EnricherType;
import brooklyn.policy.basic.AbstractEntityAdjunct;
import brooklyn.policy.basic.EnricherTypeImpl;

import com.google.common.collect.Maps;

/**
* Base {@link Enricher} implementation; all enrichers should extend this or its children
*/
public abstract class AbstractEnricher extends AbstractEntityAdjunct implements Enricher {

    private final EnricherType enricherType;
    
    /**
     * The config values of this entity. Updating this map should be done
     * via getConfig/setConfig.
     */
    
    public AbstractEnricher() {
        this(Maps.newLinkedHashMap());
    }
    
    public AbstractEnricher(Map flags) {
        super(flags);
        enricherType = new EnricherTypeImpl(getAdjunctType());
    }
    
    @Override
    public EnricherType getEnricherType() {
        return enricherType;
    }
}
