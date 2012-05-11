package brooklyn.web.console

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.management.ExecutionManager
import brooklyn.management.ManagementContext
import brooklyn.management.SubscriptionManager
import brooklyn.management.internal.LocalManagementContext
import java.util.concurrent.atomic.AtomicLong
import org.codehaus.groovy.grails.web.context.ServletContextHolder

class ManagementContextService {
    static transactional = false
    
    private ManagementContext managementContext
    protected static AtomicLong ID_GENERATOR = new AtomicLong(0L)

    Collection<Application> getApplications() {
        return context.applications
    }

    Entity getEntity(String id) {
        return context.getEntity(id)
    }

    public synchronized ManagementContext getContext() {
        if (!managementContext) {
            managementContext = (ManagementContext) ServletContextHolder.servletContext?.
                getAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT)
        }
        // TODO use a different mechanism for specifying test-app
        if (!managementContext) {
            managementContext = new LocalManagementContext();
            managementContext.manage(new TestWebApplication())
        }
        // END TODO

        return managementContext
    }

    public ExecutionManager getExecutionManager() {
        return context.executionManager
    }

    public SubscriptionManager getSubscriptionManager() {
        return context.subscriptionManager
    }


}
