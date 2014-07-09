package brooklyn.management.ha;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.rebind.persister.InMemoryObjectStore;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Ticker;

@Test(groups="Integration")
public class HighAvailabilityManagerInMemoryIntegrationTest extends HighAvailabilityManagerTestFixture {

    private static final Logger log = LoggerFactory.getLogger(HighAvailabilityManagerInMemoryIntegrationTest.class);
    
    @Override
    protected PersistenceObjectStore newPersistenceObjectStore() {
        return new InMemoryObjectStore();
    }

    @Override
    protected Duration getPollPeriod() {
        return Duration.millis(100);
    }
    
    @Override
    protected long tickerAdvance(Duration duration) {
        log.info("sleeping for "+duration);
        // actually sleep, in order to advance the local time ticker
        Time.sleep(duration);
        return super.tickerAdvance(duration);
    }

    @Override
    protected Ticker getRemoteTicker() {
        // use real times
        return null;
    }

    @Override
    @Test(groups="Integration", enabled=false, invocationCount=50) 
    public void testGetManagementPlaneStatusManyTimes() throws Exception {
    }

    @Test(groups="Integration")
    @Override
    public void testGetManagementPlaneStatus() throws Exception {
        super.testGetManagementPlaneStatus();
    }
    
    @Test(groups="Integration")
    @Override
    public void testDoesNotPromoteIfMasterTimeoutNotExpired() throws Exception {
        super.testDoesNotPromoteIfMasterTimeoutNotExpired();
    }
    
    @Test(groups="Integration")
    @Override
    public void testGetManagementPlaneSyncStateDoesNotThrowNpeBeforePersisterSet() throws Exception {
        super.testGetManagementPlaneSyncStateDoesNotThrowNpeBeforePersisterSet();
    }
    
    @Test(groups="Integration")
    @Override
    public void testGetManagementPlaneSyncStateInfersTimedOutNodeAsFailed() throws Exception {
        super.testGetManagementPlaneSyncStateInfersTimedOutNodeAsFailed();
    }
    
    @Test(groups="Integration")
    @Override
    public void testPromotes() throws Exception {
        super.testPromotes();
    }
    
}
