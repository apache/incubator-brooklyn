package brooklyn.util.mutex;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.task.Tasks;

import com.google.common.collect.ImmutableMap;

public class MutexSupport implements WithMutexes {

    private static final Logger log = LoggerFactory.getLogger(MutexSupport.class);
    private final Map<String,SemaphoreWithOwners> semaphores = new LinkedHashMap<String,SemaphoreWithOwners>();

    protected synchronized SemaphoreWithOwners getSemaphore(String mutexId) {
        return getSemaphore(mutexId, false);
    }
    // NB: the map could be "lock-striped" (hashed on mutexId) to avoid the central lock 
    protected synchronized SemaphoreWithOwners getSemaphore(String mutexId, boolean requestBeforeReturning) {
        SemaphoreWithOwners s = semaphores.get(mutexId);
        if (s==null) {
            s = new SemaphoreWithOwners(mutexId);
            semaphores.put(mutexId, s);
        }
        if (requestBeforeReturning) s.indicateCallingThreadWillRequest();
        return s;
    }
    /** forces deletion of the given mutex if it is unused; 
     * normally not required as is done automatically on close
     * (but possibly needed where there are cancelations and risk of memory leaks) */
    public synchronized void cleanupMutex(String mutexId) {
        SemaphoreWithOwners s = semaphores.get(mutexId);
        if (!s.isInUse()) semaphores.remove(mutexId);
    }
    public synchronized void cleanup() {
        Iterator<SemaphoreWithOwners> si = semaphores.values().iterator();
        while (si.hasNext()) {
            SemaphoreWithOwners s = si.next();
            if (!s.isInUse()) si.remove();
        }
    }

    @Override
    public synchronized boolean hasMutex(String mutexId) {
        SemaphoreWithOwners s = semaphores.get(mutexId);
        if (s!=null) return s.isCallingThreadAnOwner();
        return false;
    }
    
    @Override
    public void acquireMutex(String mutexId, String description) throws InterruptedException {
        SemaphoreWithOwners s = getSemaphore(mutexId, true);
        if (description!=null) Tasks.setBlockingDetails(description+" - waiting for "+mutexId);
        if (log.isDebugEnabled())
            log.debug("Acquiring mutex: "+mutexId+"@"+this+" - "+description);
        s.acquire(); 
        if (description!=null) Tasks.setBlockingDetails(null);
        s.setDescription(description);
        if (log.isDebugEnabled())
            log.debug("Acquired mutex: "+mutexId+"@"+this+" - "+description);
    }

    @Override
    public boolean tryAcquireMutex(String mutexId, String description) {
        SemaphoreWithOwners s = getSemaphore(mutexId, true);
        if (s.tryAcquire()) {
            if (log.isDebugEnabled())
                log.debug("Acquired mutex (opportunistic): "+mutexId+"@"+this+" - "+description);
            s.setDescription(description);
            return true;
        }
        return false;
    }

    @Override
    public synchronized void releaseMutex(String mutexId) {
        SemaphoreWithOwners s;
        if (log.isDebugEnabled())
            log.debug("Releasing mutex: "+mutexId+"@"+this);
        synchronized (this) { s = semaphores.get(mutexId); }
        if (s==null) throw new IllegalStateException("No mutex known for '"+mutexId+"'");
        s.release();
        cleanupMutex(mutexId);
    }
    
    @Override
    public synchronized String toString() {
        return super.toString()+"["+semaphores.size()+" semaphores: "+semaphores.values()+"]";
    }
    
    /** Returns the semaphores in use at the time the method is called, for inspection purposes (and testing).
     * The semaphores used by this class may change over time so callers are strongly discouraged
     * from manipulating the semaphore objects themselves. 
     */
    public synchronized Map<String,SemaphoreWithOwners> getAllSemaphores() {
        return ImmutableMap.<String,SemaphoreWithOwners>copyOf(semaphores);
    }
}