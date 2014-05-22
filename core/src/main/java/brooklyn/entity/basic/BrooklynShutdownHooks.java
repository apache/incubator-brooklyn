package brooklyn.entity.basic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.trait.Startable;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.javalang.Threads;

import com.google.common.collect.Lists;

public class BrooklynShutdownHooks {

    private static final Logger log = LoggerFactory.getLogger(BrooklynShutdownHooks.class);

    private static final AtomicBoolean isShutdownHookRegistered = new AtomicBoolean();
    private static final List<Entity> entitiesToStopOnShutdown = Lists.newArrayList();

    public static void invokeStopOnShutdown(Entity entity) {
        if (isShutdownHookRegistered.compareAndSet(false, true)) {
            Threads.addShutdownHook(new Runnable() {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                public void run() {
                    synchronized (entitiesToStopOnShutdown) {
                        log.info("Brooklyn stopOnShutdown shutdown-hook invoked: stopping "+entitiesToStopOnShutdown);
                        List<Task> stops = new ArrayList<Task>();
                        for (Entity entity: entitiesToStopOnShutdown) {
                            try {
                                stops.add(entity.invoke(Startable.STOP, new MutableMap()));
                            } catch (Exception exc) {
                                log.debug("stopOnShutdown of "+entity+" returned error: "+exc, exc);
                            }
                        }
                        for (Task t: stops) {
                            try {
                                log.debug("stopOnShutdown of {} completed: {}", t, t.get());
                            } catch (Exception exc) {
                                log.debug("stopOnShutdown of "+t+" returned error: "+exc, exc);
                            }
                        }
                    }
                }
            });
        }
        synchronized (entitiesToStopOnShutdown) {
            entitiesToStopOnShutdown.add(entity);
        }
    }
}
