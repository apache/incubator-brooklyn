package brooklyn.rest.resources;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.BrooklynVersion;
import brooklyn.entity.Application;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.management.Task;
import brooklyn.management.ha.ManagementPlaneSyncRecord;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.rest.api.ServerApi;
import brooklyn.rest.domain.HighAvailabilitySummary;
import brooklyn.rest.transform.HighAvailabilityTransformer;
import brooklyn.util.time.CountdownTimer;
import brooklyn.util.time.Duration;

public class ServerResource extends AbstractBrooklynRestResource implements ServerApi {

    private static final Logger log = LoggerFactory.getLogger(ServerResource.class);

    @Override
    public void reloadBrooklynProperties() {
        brooklyn().reloadBrooklynProperties();
    }

    @Override
    public void shutdown(final boolean stopAppsFirst, final long delayMillis) {
        log.info("REST call to shutdown server, stopAppsFirst="+stopAppsFirst+", delayMillis="+delayMillis);
        
        new Thread() {
            public void run() {
                Duration delayBeforeSystemExit = Duration.millis(delayMillis);
                CountdownTimer timer = delayBeforeSystemExit.countdownTimer();

                if (stopAppsFirst) {
                    List<Task<?>> stoppers = new ArrayList<Task<?>>();
                    for (Application app: mgmt().getApplications()) {
                        if (app instanceof StartableApplication)
                            stoppers.add(Entities.invokeEffector((EntityLocal)app, app, StartableApplication.STOP));
                    }
                    for (Task<?> t: stoppers) {
                        t.blockUntilEnded();
                        if (t.isError()) {
                            log.warn("Error stopping application "+t+" during shutdown (ignoring)\n"+t.getStatusDetail(true));
                        }
                    }
                }

                ((ManagementContextInternal)mgmt()).terminate(); 
                timer.waitForExpiryUnchecked();
                
                System.exit(0);
            }
        }.start();
    }

    @Override
    public String getVersion() {
        return BrooklynVersion.get();
    }

    @Override
    public String getStatus() {
        return mgmt().getHighAvailabilityManager().getNodeState().toString();
    }

    @Override
    public HighAvailabilitySummary getHighAvailability() {
        ManagementPlaneSyncRecord memento = mgmt().getHighAvailabilityManager().getManagementPlaneSyncState();
        return HighAvailabilityTransformer.highAvailabilitySummary(mgmt().getManagementNodeId(), memento);
    }
}
