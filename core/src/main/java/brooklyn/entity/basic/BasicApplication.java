package brooklyn.entity.basic;

import brooklyn.entity.Application;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;

/**
 * The most basic implementation of an application possible.
 * Used (internally) for the default type of application to be built.
 * 
 * @author aled
 */
@ImplementedBy(BasicApplicationImpl.class)
public interface BasicApplication extends Application, Startable {
}
