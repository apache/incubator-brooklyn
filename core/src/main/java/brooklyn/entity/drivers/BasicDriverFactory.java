package brooklyn.entity.drivers;

import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import com.google.common.base.Throwables;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class BasicDriverFactory implements EntityDriverFactory {

    @Override
   public <D extends EntityDriver> D build(DriverDependentEntity<D> entity, Location location){
        Class<D> driverInterface = entity.getDriverInterface();
        Class<? extends D> driverClass;
        if (driverInterface.isInterface()) {
            if (location instanceof SshMachineLocation) {
                String driverInterfaceName = driverInterface.getName();
                if (!driverInterfaceName.endsWith("Driver")) {
                    //TODO: Improve
                    throw new RuntimeException("Driver name doesn't end with driver; " + driverInterfaceName);
                }

                String driverClassName = driverInterfaceName.replace("Driver", "SshDriver");
                try {
                    driverClass = (Class<? extends D>) entity.getClass().getClassLoader().loadClass(driverClassName);
                } catch (ClassNotFoundException e) {
                    throw Throwables.propagate(e);
                }
            } else {
                //TODO: Improve
                throw new RuntimeException("Currently only SshMachineLocation is supported");
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

    private Constructor<EntityDriver> getConstructor(Class<? extends EntityDriver> driverClass) {
        for (Constructor constructor : driverClass.getConstructors()) {
            if (constructor.getParameterTypes().length == 2) {
                return constructor;
            }
        }

        //TODO:
        throw new RuntimeException();
    }
}
