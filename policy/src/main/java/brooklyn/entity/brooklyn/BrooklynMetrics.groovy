package brooklyn.entity.brooklyn

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey
import brooklyn.management.internal.AbstractManagementContext
import brooklyn.management.internal.LocalSubscriptionManager
import brooklyn.util.flags.SetFromFlag
import brooklyn.util.task.BasicExecutionManager

import com.google.common.util.concurrent.ThreadFactoryBuilder

public class BrooklynMetrics extends AbstractEntity {

    @SetFromFlag("updatePeriod")
    public static final BasicAttributeSensorAndConfigKey<Long> UPDATE_PERIOD = [ Long, "brooklyn.metrics.updatePeriod", "Time (in milliseconds) between refreshing the metrics", 1000L ]

    public static final BasicAttributeSensor<Long> TOTAL_EFFECTORS_INVOKED = [ Long, "brooklyn.metrics.totalEffectorsInvoked", "Total number of effector calls that have been made" ]
    
    public static final BasicAttributeSensor<Long> TOTAL_TASKS_SUBMITTED = [ Long, "brooklyn.metrics.totalTasksSubmitted", "Total number of tasks that have been executed by brooklyn" ]
    
    public static final BasicAttributeSensor<Long> NUM_INCOMPLETE_TASKS = [ Long, "brooklyn.metrics.numIncompleteTasks", "Number of tasks that have been submitted but that have not yet completed" ]
    
    public static final BasicAttributeSensor<Integer> NUM_ACTIVE_TASKS = [ Integer, "brooklyn.metrics.numActiveTasks", "Number of currently active tasks being executed" ]

    public static final BasicAttributeSensor<Long> TOTAL_EVENTS_PUBLISHED = [ Long, "brooklyn.metrics.totalEventsPublished", "Total number of events published" ]
    
    public static final BasicAttributeSensor<Long> TOTAL_EVENTS_DELIVERED = [ Long, "brooklyn.metrics.totalEventsDelivered", "Total number of events delivered (counting an event multiple times if more than one subscriber)" ]
    
    public static final BasicAttributeSensor<Long> NUM_SUBSCRIPTIONS = [ Long, "brooklyn.metrics.numSubscriptions", "Current number of event subscriptions" ]
    
    private ScheduledExecutorService executor;
    
    public BrooklynMetrics(Map props, Entity parent=null) {
        super(props, parent)
    }
    
    public void onManagementBecomingMaster() {
        // TODO Don't use own thread pool; use new "feeds" (see FunctionFeed, or variants there of)
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("brooklyn-brooklynmetrics-poller-%d")
                .build();
        executor = Executors.newSingleThreadScheduledExecutor(threadFactory)
        executor.scheduleWithFixedDelay(
                { refreshSensors() }, 
                0, 
                getConfig(UPDATE_PERIOD), 
                TimeUnit.MILLISECONDS)
    }
    
    /**
     * Invoked by {@link ManagementContext} when this entity becomes mastered at a particular management node,
     * including the final management end and subsequent management node master-change for this entity.
     */
    public void onManagementNoLongerMaster() {
        if (executor != null) executor.shutdownNow()
    }

    private void refreshSensors() {
        AbstractManagementContext managementContext = (AbstractManagementContext) getManagementContext()
        BasicExecutionManager execManager = (BasicExecutionManager) getManagementContext()?.getExecutionManager()
        LocalSubscriptionManager subsManager = (LocalSubscriptionManager) getManagementContext()?.getSubscriptionManager()
        
        if (managementContext != null) {
            setAttribute(TOTAL_EFFECTORS_INVOKED, managementContext.getTotalEffectorInvocations())
        }
        if (execManager != null) {
            setAttribute(TOTAL_TASKS_SUBMITTED, execManager.getTotalTasksSubmitted())
            setAttribute(NUM_INCOMPLETE_TASKS, execManager.getNumIncompleteTasks())
            setAttribute(NUM_ACTIVE_TASKS, execManager.getNumActiveTasks())
        }
        if (subsManager != null) {
            setAttribute(TOTAL_EVENTS_PUBLISHED, subsManager.getTotalEventsPublished())
            setAttribute(TOTAL_EVENTS_DELIVERED, subsManager.getTotalEventsDelivered())
            setAttribute(NUM_SUBSCRIPTIONS, subsManager.getNumSubscriptions())
        }
    }
}
