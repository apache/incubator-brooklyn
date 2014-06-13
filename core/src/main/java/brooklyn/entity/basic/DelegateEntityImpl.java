package brooklyn.entity.basic;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;

import com.google.common.base.Preconditions;

public class DelegateEntityImpl extends AbstractEntity implements DelegateEntity {

    static {
        EntityUrl.init();
    }

    @Override
    public void init() {
        Entity delegate = getConfig(DELEGATE_ENTITY);
        Preconditions.checkNotNull(delegate, "delegate");

        // Propagate all sensors from the delegate entity
        addEnricher(Enrichers.builder()
                .propagatingAll()
                .from(delegate)
                .build());

        // Publish the entity as an attribute for linking
        setAttribute(DELEGATE_ENTITY, delegate);
        setAttribute(DELEGATE_ENTITY_LINK, EntityUrl.entityUrl().apply(delegate));
    }
}

