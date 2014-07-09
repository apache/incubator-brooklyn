package brooklyn.entity.drivers;

import brooklyn.location.Location;

public class BasicEntityDriverManager implements EntityDriverManager {

    private final RegistryEntityDriverFactory registry;
    private final ReflectiveEntityDriverFactory reflective;
    
    public BasicEntityDriverManager() {
        registry = new RegistryEntityDriverFactory();
        reflective = new ReflectiveEntityDriverFactory();
    }
    
    public <D extends EntityDriver> void registerDriver(Class<D> driverInterface, Class<? extends Location> locationClazz, Class<? extends D> driverClazz) {
        registry.registerDriver(driverInterface, locationClazz, driverClazz);
    }
    
    @Override
    public <D extends EntityDriver> D build(DriverDependentEntity<D> entity, Location location){
        if (registry.hasDriver(entity, location)) {
            return registry.build(entity, location);
        } else {
            return reflective.build(entity, location);
        }
    }
}