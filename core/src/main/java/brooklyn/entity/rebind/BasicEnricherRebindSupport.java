package brooklyn.entity.rebind;

import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.rebind.dto.MementosGenerators;
import brooklyn.mementos.EnricherMemento;
import brooklyn.enricher.basic.AbstractEnricher;

public class BasicEnricherRebindSupport implements RebindSupport<EnricherMemento> {

    private static final Logger LOG = LoggerFactory.getLogger(BasicEnricherRebindSupport.class);
    
    private final AbstractEnricher enricher;
    
    public BasicEnricherRebindSupport(AbstractEnricher enricher) {
        this.enricher = enricher;
    }
    
    @Override
    public EnricherMemento getMemento() {
        return getMementoWithProperties(Collections.<String,Object>emptyMap());
    }

    protected EnricherMemento getMementoWithProperties(Map<String,?> props) {
        EnricherMemento memento = MementosGenerators.newEnricherMementoBuilder(enricher).customFields(props).build();
        if (LOG.isTraceEnabled()) LOG.trace("Creating memento for enricher: {}", memento.toVerboseString());
        return memento;
    }

    @Override
    public void reconstruct(RebindContext rebindContext, EnricherMemento memento) {
        if (LOG.isTraceEnabled()) LOG.trace("Reconstructing enricher: {}", memento.toVerboseString());

        // Note that the flags have been set in the constructor
        enricher.setName(memento.getDisplayName());
        
        doReconsruct(rebindContext, memento);
    }

    /**
     * For overriding, to give custom reconsruct behaviour.
     */
    protected void doReconsruct(RebindContext rebindContext, EnricherMemento memento) {
        // default is no-op
    }
}
