package brooklyn.entity.rebind;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.rebind.dto.MementosGenerators;
import brooklyn.mementos.PolicyMemento;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.FlagUtils;

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

        policy.setName(memento.getDisplayName());

        // TODO entity does config-lookup differently; the memento contains the config keys.
        // BasicEntityMemento.postDeserialize uses the injectTypeClass to call EntityTypes.getDefinedConfigKeys(clazz)
        // 
        // Note that the flags may have been set in the constructor; but some policies have no-arg constructors
        ConfigBag configBag = ConfigBag.newInstance(memento.getConfig());
        FlagUtils.setFieldsFromFlags(policy, configBag);
        FlagUtils.setAllConfigKeys(policy, configBag, false);
        
        doReconsruct(rebindContext, memento);
        ((AbstractPolicy)policy).rebind();
    }

    /**
     * For overriding, to give custom reconsruct behaviour.
     */
    protected void doReconsruct(RebindContext rebindContext, PolicyMemento memento) {
        // default is no-op
    }
}
