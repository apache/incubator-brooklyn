package brooklyn.entity.basic;

import java.util.LinkedHashMap;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.EntityType;
import brooklyn.event.Sensor;
import brooklyn.util.exceptions.Exceptions;

public class EntityTypes {

    private static class ImmutableEntityType extends EntityDynamicType {
        public ImmutableEntityType(Class<? extends Entity> clazz) {
            super(clazz);
        }
        @Override
        public void addSensor(Sensor<?> newSensor) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void addSensorIfAbsent(Sensor<?> newSensor) {
            throw new UnsupportedOperationException();
        }
        @Override
        public Sensor<?> addSensorIfAbsentWithoutPublishing(Sensor<?> newSensor) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void addSensors(Iterable<? extends Sensor<?>> newSensors) {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean removeSensor(Sensor<?> sensor) {
            throw new UnsupportedOperationException();
        }
        @Override
        public Sensor<?> removeSensor(String sensorName) {
            throw new UnsupportedOperationException();
        }
    }
    
    @SuppressWarnings("rawtypes")
    private static final Map<Class,ImmutableEntityType> cache = new LinkedHashMap<Class,ImmutableEntityType>();
    
    public static EntityDynamicType getDefinedEntityType(Class<? extends Entity> entityClass) {
        ImmutableEntityType t = cache.get(entityClass);
        if (t!=null) return t;
        return loadDefinedEntityType(entityClass);
    }

    private static synchronized EntityDynamicType loadDefinedEntityType(Class<? extends Entity> entityClass) {
        ImmutableEntityType type = cache.get(entityClass);
        if (type!=null) return type;
        type = new ImmutableEntityType(entityClass);
        cache.put(entityClass, type);
        return type;
    }

    public static Map<String, ConfigKey<?>> getDefinedConfigKeys(Class<? extends Entity> entityClass) {
        return getDefinedEntityType(entityClass).getConfigKeys();
    }
    @SuppressWarnings("unchecked")
    public static Map<String, ConfigKey<?>> getDefinedConfigKeys(String entityTypeName) {
        try {
            return getDefinedConfigKeys((Class<? extends Entity>) Class.forName(entityTypeName));
        } catch (ClassNotFoundException e) {
            throw Exceptions.propagate(e);
        }
    }
    
    public static Map<String, Sensor<?>> getDefinedSensors(Class<? extends Entity> entityClass) {
        return getDefinedEntityType(entityClass).getSensors();
    }
    @SuppressWarnings("unchecked")
    public static Map<String, Sensor<?>> getDefinedSensors(String entityTypeName) {
        try {
            return getDefinedSensors((Class<? extends Entity>) Class.forName(entityTypeName));
        } catch (ClassNotFoundException e) {
            throw Exceptions.propagate(e);
        }
    }
    
}
