package brooklyn.entity.basic;

import brooklyn.config.render.RendererHints;
import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;

public class DelegateEntityImpl extends AbstractEntity implements DelegateEntity {
    
    public DelegateEntityImpl() { }

    @Override
    public void init() {
    	Entity delegate = getConfig(DELEGATE_ENTITY);
    	setAttribute(DELEGATE_ENTITY, delegate);
    	Preconditions.checkNotNull(delegate, "delegate");
    	addEnricher(Enrichers.builder()
    			.propagatingAll()
    			.from(delegate)
    			.build());
    }

	static {
		RendererHints.register(DELEGATE_ENTITY, new RendererHints.NamedActionWithUrl("Open",
				new Function<Object, String>() {
					@Override
					public String apply(Object input) {
						if (input instanceof Entity) {
							Entity entity = (Entity) input;
							String url = String.format("#/v1/applications/%s/entities/%s", entity.getApplicationId(), entity.getId());
							return url;
						} else {
							return null;
						}
					}
				}));
	}
}

