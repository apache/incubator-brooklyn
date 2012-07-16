package brooklyn.entity.basic;

import brooklyn.entity.Entity;
import com.google.common.base.Throwables;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class BasicConfigurableEntityFactory<T extends Entity> extends AbstractConfigurableEntityFactory<T> {
    private final Class<T> clazz;

    public BasicConfigurableEntityFactory(Class<T> clazz) {
        this(new HashMap(), clazz);
    }

    public BasicConfigurableEntityFactory(Map flags, Class<T> clazz) {
        super(flags);
        this.clazz = clazz;
    }

    public T newEntity2(Map flags, Entity owner) {
        try {
            Constructor<T> constructor = clazz.getConstructor(Map.class, Entity.class);
            return constructor.newInstance(flags, owner);
        } catch (InstantiationException e) {
            throw Throwables.propagate(e);
        } catch (IllegalAccessException e) {
            throw Throwables.propagate(e);
        } catch (InvocationTargetException e) {
            throw Throwables.propagate(e);
        } catch (NoSuchMethodException e) {
            throw Throwables.propagate(e);
        }
    }
}
