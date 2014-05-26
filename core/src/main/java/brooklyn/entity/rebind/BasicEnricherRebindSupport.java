package brooklyn.entity.rebind;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.rebind.dto.MementosGenerators;
import brooklyn.mementos.EnricherMemento;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.FlagUtils;

public class BasicEnricherRebindSupport implements RebindSupport<EnricherMemento> {

    private static final Logger LOG = LoggerFactory.getLogger(BasicEnricherRebindSupport.class);
    
    private final AbstractEnricher enricher;
    
    public BasicEnricherRebindSupport(AbstractEnricher enricher) {
        this.enricher = enricher;
    }
    
    @Override
    public EnricherMemento getMemento() {
        EnricherMemento memento = MementosGenerators.newEnricherMementoBuilder(enricher).build();
        if (LOG.isTraceEnabled()) LOG.trace("Creating memento for enricher: {}", memento.toVerboseString());
        return memento;
    }

    @Override
    public void reconstruct(RebindContext rebindContext, EnricherMemento memento) {
        if (LOG.isTraceEnabled()) LOG.trace("Reconstructing enricher: {}", memento.toVerboseString());

        enricher.setName(memento.getDisplayName());
        
        // FIXME Will this set config keys that are not marked with `@SetFromFlag`?
        // Note that the flags may have been set in the constructor; but some have no-arg constructors
        ConfigBag configBag = ConfigBag.newInstance(memento.getConfig());
        FlagUtils.setFieldsFromFlags(enricher, configBag);
        
        doReconsruct(rebindContext, memento);
    }


    /**
     * For overriding, to give custom reconsruct behaviour.
     */
    protected void doReconsruct(RebindContext rebindContext, EnricherMemento memento) {
        // default is no-op
    }
}
