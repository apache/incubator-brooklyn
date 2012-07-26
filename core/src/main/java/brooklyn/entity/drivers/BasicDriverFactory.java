package brooklyn.entity.drivers;

import brooklyn.entity.basic.lifecycle.StartStopDriver;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import com.google.common.base.Throwables;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class BasicDriverFactory implements DriverFactory {

    @Override
    public StartStopDriver build(DriverAwareEntity entity, Location location) {
        Class<? extends StartStopDriver> driverInterface = entity.getDriverInterface();

        Class<? extends StartStopDriver> driverClass;
        if (driverInterface.isInterface()) {

            if (location instanceof SshMachineLocation) {
                String driverInterfaceName = driverInterface.getName();
                if (!driverInterfaceName.endsWith("Driver")) {
                    //TODO: Improve
                    throw new RuntimeException("Driver name doesn't end with driver; "+driverInterfaceName);
                }

                String driverClassName = driverInterfaceName.replace("Driver", "SshDriver");
                try {
                    driverClass = (Class<? extends StartStopDriver>) entity.getClass().getClassLoader().loadClass(driverClassName);
                } catch (ClassNotFoundException e) {
                    throw Throwables.propagate(e);
                }
            } else {
                //TODO: Improve
                throw new RuntimeException();
            }
        } else {
            driverClass = driverInterface;
        }

        Constructor constructor = getConstructor(driverClass);
        try {
            constructor.setAccessible(true);
            return (StartStopDriver) constructor.newInstance(entity, location);
        } catch (InstantiationException e) {
            throw Throwables.propagate(e);
        } catch (IllegalAccessException e) {
            throw Throwables.propagate(e);
        } catch (InvocationTargetException e) {
            throw Throwables.propagate(e);
        }
    }

    private Constructor getConstructor(Class<? extends StartStopDriver> driverClass) {
        for (Constructor constructor : driverClass.getConstructors()) {
            if (constructor.getParameterTypes().length == 2) {
                return constructor;
            }
        }

        //TODO:
        throw new RuntimeException();
    }
}
