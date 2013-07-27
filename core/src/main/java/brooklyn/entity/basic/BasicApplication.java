package brooklyn.entity.basic;

import brooklyn.entity.proxying.ImplementedBy;

/**
 * The most basic implementation of an application possible.
 * Used (internally) for the default type of application to be built.
 * 
 * @author aled
 */
@ImplementedBy(BasicApplicationImpl.class)
public interface BasicApplication extends StartableApplication {
}
