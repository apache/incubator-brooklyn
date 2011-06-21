package brooklyn.management.internal;

import brooklyn.entity.Application;
import brooklyn.management.ManagementContext;

public abstract class AbstractManagementContext implements ManagementContext {
	public abstract void registerApplication(Application app);
}
