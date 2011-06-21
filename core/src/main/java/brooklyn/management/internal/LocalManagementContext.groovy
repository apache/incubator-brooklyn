package brooklyn.management.internal;

import java.util.Collection
import java.util.Set

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.management.ExecutionManager
import brooklyn.util.task.BasicExecutionManager
import brooklyn.util.task.ExecutionContext

public class LocalManagementContext extends AbstractManagementContext {

	Set<Application> apps = []
	                         
	@Override
	public void registerApplication(Application app) {
		apps.add(app);
	}
	
	@Override
	public Collection<Application> getApplications() {
		return apps
	}
	
	@Override
	public Entity getEntity(String id) {
		// TODO Auto-generated method stub
		return null;
	}

	//TODO subscriptions
	
    private SubscriptionManager subscriptions = new LocalSubscriptionManager();
    public SubscriptionManager getSubscriptionManager() { return subscriptions; }

	
	private ExecutionManager execution = new BasicExecutionManager();
	public ExecutionManager getExecutionManager() { return execution; }
	public ExecutionContext getExecutionContext(Entity e) { return new ExecutionContext(tag: e, execution); }
	
}
