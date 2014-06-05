package brooklyn.enricher.basic;

import java.util.Map;

import brooklyn.entity.rebind.BasicEnricherRebindSupport;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.mementos.EnricherMemento;
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
    
    public AbstractEnricher() {
        this(Maps.newLinkedHashMap());
    }
    
    public AbstractEnricher(Map flags) {
        super(flags);
        enricherType = new EnricherTypeImpl(getAdjunctType());
        
        if (isLegacyConstruction() && !isLegacyNoConstructionInit()) {
            init();
        }
    }

    @Override
    public RebindSupport<EnricherMemento> getRebindSupport() {
        return new BasicEnricherRebindSupport(this);
    }
    
    @Override
    public EnricherType getEnricherType() {
        return enricherType;
    }

    @Override
    protected void onChanged() {
        getManagementContext().getRebindManager().getChangeListener().onChanged(this);
    }
}
