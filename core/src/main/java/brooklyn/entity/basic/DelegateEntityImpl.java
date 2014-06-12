package brooklyn.entity.basic;

import com.google.common.base.Preconditions;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;

public class DelegateEntityImpl extends AbstractEntity implements DelegateEntity {
    
    public DelegateEntityImpl() { }

    @Override
    public void init() {
    	Entity delegate = getConfig(DELEGATE_ENTITY);
    	Preconditions.checkNotNull(delegate, "delegate");
    	addEnricher(Enrichers.builder()
    			.propagatingAll()
    			.from(delegate)
    			.build());
    }

}
