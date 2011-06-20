package brooklyn.management.internal;

import java.util.Collection
import java.util.Set

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.EntitySummary
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
	public Collection<EntitySummary> getApplicationSummaries() {
		apps.collect { it.getImmutableSummary() }
	}
	
	@Override
	public Collection<EntitySummary> getEntitySummariesInApplication(String id) {
		// TODO Auto-generated method stub
		
		// FIXME are these really needed? if we can get children of an entity, and other entities contained by an entity...
		
		return null;
	}
	@Override
	public Collection<EntitySummary> getAllEntitySummaries() {
		// TODO Auto-generated method stub
		return null;
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
