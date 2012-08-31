package brooklyn.entity.drivers;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;

import com.google.common.base.Throwables;

/**
 * Follows a class naming convention: the driver interface must end in "Driver", and the implementation 
 * must match the driver interface name but with a suffix like "SshDriver" instead of "Driver".
 * 
 * Reflectively instantiates and returns the driver, based on the location passed in.
 * 
 * @author Peter Veentjer
 */
public class ReflectiveEntityDriverFactory implements EntityDriverFactory {

    @Override
    public <D extends EntityDriver> D build(DriverDependentEntity<D> entity, Location location){
        Class<D> driverInterface = entity.getDriverInterface();
        Class<? extends D> driverClass;
        if (driverInterface.isInterface()) {
            String driverClassName = inferClassName(driverInterface, location);
            try {
                System.out.println("Loading "+driverInterface.getName());
                entity.getClass().getClassLoader().loadClass(driverInterface.getName());
            } catch (ClassNotFoundException e) {
                throw Throwables.propagate(e);
            }
            try {
                System.out.println("Loading "+driverClassName);
                driverClass = (Class<? extends D>) entity.getClass().getClassLoader().loadClass(driverClassName);
            } catch (ClassNotFoundException e) {
                throw Throwables.propagate(e);
            }
        } else {
            driverClass = driverInterface;
        }

        Constructor constructor = getConstructor(driverClass);
        try {
            constructor.setAccessible(true);
            return (D) constructor.newInstance(entity, location);
        } catch (InstantiationException e) {
            throw Throwables.propagate(e);
        } catch (IllegalAccessException e) {
            throw Throwables.propagate(e);
        } catch (InvocationTargetException e) {
            throw Throwables.propagate(e);
        }
    }

    private String inferClassName(Class<? extends EntityDriver> driverInterface, Location location) {
        String driverInterfaceName = driverInterface.getName();
        
        if (location instanceof SshMachineLocation) {
            if (!driverInterfaceName.endsWith("Driver")) {
                throw new RuntimeException(String.format("Driver name [%s] doesn't end with 'Driver'",driverInterfaceName));
            }

            return driverInterfaceName.substring(0, driverInterfaceName.length()-"Driver".length())+"SshDriver";
        } else {
            //TODO: Improve
            throw new RuntimeException("Currently only SshMachineLocation is supported, but location="+location+" for driver +"+driverInterface);
        }
    }
    
    private Constructor<EntityDriver> getConstructor(Class<? extends EntityDriver> driverClass) {
        for (Constructor constructor : driverClass.getConstructors()) {
            if (constructor.getParameterTypes().length == 2) {
                return constructor;
            }
        }

        //TODO:
        throw new RuntimeException(String.format("Class [%s] has no constructor with 2 arguments",driverClass.getName()));
    }
}
