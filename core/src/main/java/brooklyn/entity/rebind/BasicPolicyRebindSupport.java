package brooklyn.entity.rebind;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.rebind.dto.MementosGenerators;
import brooklyn.mementos.PolicyMemento;
import brooklyn.policy.basic.AbstractPolicy;

public class BasicPolicyRebindSupport implements RebindSupport<PolicyMemento> {

    private static final Logger LOG = LoggerFactory.getLogger(BasicPolicyRebindSupport.class);
    
    private final AbstractPolicy policy;
    
    public BasicPolicyRebindSupport(AbstractPolicy policy) {
        this.policy = policy;
    }
    
    @Override
    public PolicyMemento getMemento() {
        PolicyMemento memento = MementosGenerators.newPolicyMementoBuilder(policy).build();
        if (LOG.isTraceEnabled()) LOG.trace("Creating memento for policy: {}", memento.toVerboseString());
        return memento;
    }

    @Override
    public void reconstruct(RebindContext rebindContext, PolicyMemento memento) {
        if (LOG.isTraceEnabled()) LOG.trace("Reconstructing policy: {}", memento.toVerboseString());

        // Note that the flags have been set in the constructor
        policy.setName(memento.getDisplayName());
        
        doReconsruct(rebindContext, memento);
    }

    /**
     * For overriding, to give custom reconsruct behaviour.
     */
    protected void doReconsruct(RebindContext rebindContext, PolicyMemento memento) {
        // default is no-op
    }
}
