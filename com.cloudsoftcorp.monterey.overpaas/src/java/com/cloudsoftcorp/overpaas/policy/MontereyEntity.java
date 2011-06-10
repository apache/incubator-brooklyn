package com.cloudsoftcorp.overpaas.policy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.overpaas.activity.Event;
import org.overpaas.activity.EventFilter;
import org.overpaas.activity.EventListener;
import org.overpaas.policy.Entity;

import com.cloudsoftcorp.monterey.control.controltool.CdmControlClientAspects.CdmEventDispatcher;
import com.cloudsoftcorp.monterey.control.controltool.CdmControlClientAspects.NodeLoadListener;
import com.cloudsoftcorp.monterey.control.workrate.api.WorkrateReport;
import com.cloudsoftcorp.monterey.control.workrate.basic.MachineLoadReport;
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NetworkInfo;
import com.cloudsoftcorp.monterey.network.control.plane.ManagementNode;
import com.cloudsoftcorp.util.Loggers;
import com.cloudsoftcorp.util.condition.Filter;
import com.cloudsoftcorp.util.exception.RuntimeInterruptedException;

public abstract class MontereyEntity implements Entity {

    // FIXME Leaks the executor thread
    
    private static final Logger LOG = Loggers.getLoggerForClass();
    
    private final ManagementNode managementNode;
    private final Dmn1NetworkInfo networkInfo;
    private final CdmEventDispatcher eventDispatcher;
    private final Map<EventFilter, EventListener> subscriptions = new LinkedHashMap<EventFilter, EventListener>();
    private final ScheduledExecutorService notificationExecutor = Executors.newScheduledThreadPool(1);
    private final AtomicBoolean requiresSubscriberNotification = new AtomicBoolean(true);
    
    public MontereyEntity(ManagementNode managementNode) {
        this.managementNode = managementNode;
        this.networkInfo = managementNode.getNetworkInfo();
        this.eventDispatcher = managementNode.getEventDispatcher();

        eventDispatcher.addLoadListener(new NodeLoadListener() {
            @Override public void onWorkrateReport(WorkrateReport report) {
                requiresSubscriberNotification.set(true);
            }
            @Override public void onMachineLoadReport(MachineLoadReport report) {
            }
        });
        
        notificationExecutor.scheduleAtFixedRate(new Runnable() {
                @Override public void run() {
                    if (requiresSubscriberNotification.compareAndSet(true, false)) {
                        doNotifySubscribers();
                    }
                }}, 
                1000, 1000, TimeUnit.MILLISECONDS);
    }

    protected abstract void doNotifySubscribers();

    protected ManagementNode getManagementNode() {
        return managementNode;
    }
    
    protected Dmn1NetworkInfo getNetworkInfo() {
        return networkInfo;
    }
    
    @Override
    public void subscribe(EventFilter filter, EventListener listener) {
        // FIXME Not production quality! Relies on filter not implementing equals!
        subscriptions.put(filter, listener);
    }

    @Override
    public void subscribeToChildren(Filter<Entity> childFilter, EventFilter eventFilter, EventListener listener) {
        // FIXME ???
        subscriptions.put(eventFilter, listener);
    }

    @Override
    public void raiseEvent(Event event) {
        notifySubscribers(event);
    }

    protected void notifySubscribers(Event event) {
        for (Map.Entry<EventFilter, EventListener> entry : subscriptions.entrySet()) {
            try {
                if (entry.getKey().accept(event)) {
                    entry.getValue().onEvent(event);
                }
            } catch (RuntimeInterruptedException e) {
                throw e;
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Error in event subscriber, when processing "+event, e);
            }
        }
    }
}
