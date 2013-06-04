package brooklyn.entity.brooklyn;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import brooklyn.entity.basic.AbstractEntity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalSubscriptionManager;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.task.BasicExecutionManager;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class BrooklynMetrics extends AbstractEntity {

    @SetFromFlag("updatePeriod")
    public static final BasicAttributeSensorAndConfigKey<Long> UPDATE_PERIOD = new BasicAttributeSensorAndConfigKey<Long>(
            Long.class, "brooklyn.metrics.updatePeriod", "Time (in milliseconds) between refreshing the metrics", 1000L);

    public static final AttributeSensor<Long> TOTAL_EFFECTORS_INVOKED = new BasicAttributeSensor<Long>(
            Long.class, "brooklyn.metrics.totalEffectorsInvoked", "Total number of effector calls that have been made");
    
    public static final AttributeSensor<Long> TOTAL_TASKS_SUBMITTED = new BasicAttributeSensor<Long>(
            Long.class, "brooklyn.metrics.totalTasksSubmitted", "Total number of tasks that have been executed by brooklyn");
    
    public static final AttributeSensor<Long> NUM_INCOMPLETE_TASKS = new BasicAttributeSensor<Long>(
            Long.class, "brooklyn.metrics.numIncompleteTasks", "Number of tasks that have been submitted but that have not yet completed");
    
    public static final AttributeSensor<Long> NUM_ACTIVE_TASKS = new BasicAttributeSensor<Long>(
            Long.class, "brooklyn.metrics.numActiveTasks", "Number of currently active tasks being executed");

    public static final AttributeSensor<Long> TOTAL_EVENTS_PUBLISHED = new BasicAttributeSensor<Long>(
            Long.class, "brooklyn.metrics.totalEventsPublished", "Total number of events published");
    
    public static final AttributeSensor<Long> TOTAL_EVENTS_DELIVERED = new BasicAttributeSensor<Long>(
            Long.class, "brooklyn.metrics.totalEventsDelivered", "Total number of events delivered (counting an event multiple times if more than one subscriber)");
    
    public static final AttributeSensor<Long> NUM_SUBSCRIPTIONS = new BasicAttributeSensor<Long>(
            Long.class, "brooklyn.metrics.numSubscriptions", "Current number of event subscriptions");
    
    private ScheduledExecutorService executor;
    
    public BrooklynMetrics() {
    }

    @Override
    public void onManagementBecomingMaster() {
        // TODO Don't use own thread pool; use new "feeds" (see FunctionFeed, or variants there of)
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("brooklyn-brooklynmetrics-poller-%d")
                .build();
        executor = Executors.newSingleThreadScheduledExecutor(threadFactory);
        executor.scheduleWithFixedDelay(
                new Runnable() {
                    public void run() {
                        refreshSensors();
                    }}, 
                0, 
                getConfig(UPDATE_PERIOD), 
                TimeUnit.MILLISECONDS);
    }
    
    /**
     * Invoked by {@link ManagementContext} when this entity becomes mastered at a particular management node,
     * including the final management end and subsequent management node master-change for this entity.
     */
    @Override
    public void onManagementNoLongerMaster() {
        if (executor != null) executor.shutdownNow();
    }

    private void refreshSensors() {
        ManagementContext managementContext = getManagementContext();
        BasicExecutionManager execManager = (BasicExecutionManager) (managementContext != null ? managementContext.getExecutionManager() : null);
        LocalSubscriptionManager subsManager = (LocalSubscriptionManager) (managementContext != null ? managementContext.getSubscriptionManager() : null);
        
        if (managementContext != null) {
            setAttribute(TOTAL_EFFECTORS_INVOKED, ((ManagementContextInternal)managementContext).getTotalEffectorInvocations());
        }
        if (execManager != null) {
            setAttribute(TOTAL_TASKS_SUBMITTED, execManager.getTotalTasksSubmitted());
            setAttribute(NUM_INCOMPLETE_TASKS, execManager.getNumIncompleteTasks());
            setAttribute(NUM_ACTIVE_TASKS, execManager.getNumActiveTasks());
        }
        if (subsManager != null) {
            setAttribute(TOTAL_EVENTS_PUBLISHED, subsManager.getTotalEventsPublished());
            setAttribute(TOTAL_EVENTS_DELIVERED, subsManager.getTotalEventsDelivered());
            setAttribute(NUM_SUBSCRIPTIONS, subsManager.getNumSubscriptions());
        }
    }
}
