package brooklyn.entity.basic;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import com.google.common.base.Function;

import brooklyn.config.render.RendererHints;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;

/**
 * A delegate entity for use as a {@link Group} child proxy for members.
 */
@ImplementedBy(DelegateEntityImpl.class)
public interface DelegateEntity extends Entity {

    AttributeSensorAndConfigKey<Entity, Entity> DELEGATE_ENTITY = ConfigKeys.newSensorAndConfigKey(Entity.class, "delegate.entity", "The delegate entity");

    AttributeSensor<String> DELEGATE_ENTITY_LINK = Sensors.newStringSensor("webapp.url", "The delegate entity link");

    /** Hints for rendering the delegate entity as a link in the Brooklyn console UI. */
    public static class EntityUrl {

        private static final AtomicBoolean initialized = new AtomicBoolean(false);
        private static final Function<Object, String> entityUrlFunction = new Function<Object, String>() {
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
        };

        public static Function<Object, String> entityUrl() { return entityUrlFunction; }

        /** Setup renderer hints. */
        @SuppressWarnings("rawtypes")
        public static void init() {
            if (initialized.getAndSet(true)) return;

            RendererHints.register(DELEGATE_ENTITY, new RendererHints.NamedActionWithUrl("Open", entityUrl()));
            RendererHints.register(DELEGATE_ENTITY_LINK, new RendererHints.NamedActionWithUrl("Open"));
        }

        static {
            init();
        }
    }

}
