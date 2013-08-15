package brooklyn.entity.basic;

import static com.google.common.base.Preconditions.checkNotNull;
import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;

import com.google.common.base.Objects;
import com.google.common.base.Supplier;

/**
 * A tuple containing an {@link Entity} and an {@link Attribute}, which is assumed to be present on the entity.
 * <p>
 * Allows retrieval of the attribute {@link #getValue() value} or can be used instead where a {@link Supplier} for
 * the attribute value is required.
 */
public class EntityAndAttribute<T> implements Supplier<T> {

    private final Entity entity;
    private final AttributeSensor<T> attribute;

    public EntityAndAttribute(Entity entity, AttributeSensor<T> attribute) {
      this.entity = checkNotNull(entity, "entity");
      this.attribute = checkNotNull(attribute, "attribute");
    }

    public Entity getEntity() {
        return entity;
    }

    public AttributeSensor<T> getAttribute() {
        return attribute;
    }

    public T getValue() {
        return entity.getAttribute(attribute);
    }

    public void setValue(T val) {
        ((EntityLocal)entity).setAttribute(attribute, val);
    }

    /**
     * {@inheritDoc}
     *
     * Returns the current value of the {@link #getAttribute() attribute} on the {@link #getEntity() entity}.
     *
     * @see #getValue()
     */
    @Override
    public T get() {
        return getValue();
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("entity", entity)
                .add("attribute", attribute)
                .toString();
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(entity, attribute);
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (!(o instanceof EntityAndAttribute)) return false;
        EntityAndAttribute<?> that = (EntityAndAttribute<?>) o;
        return Objects.equal(this.entity, that.entity) &&
                Objects.equal(this.attribute, that.attribute);
    }

}
