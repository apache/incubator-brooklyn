package brooklyn.util.mutex;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import brooklyn.management.Task;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.task.BasicTask;

import com.google.common.collect.ImmutableMap;

public class MutexSupport implements WithMutexes {

    private Map<String,SemaphoreWithOwners> semaphores = new LinkedHashMap<String,SemaphoreWithOwners>();

    protected synchronized SemaphoreWithOwners getSemaphore(String mutexId) {
        return getSemaphore(mutexId, false);
    }
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
        Task current = BasicExecutionManager.getCurrentTask();
        if (current instanceof BasicTask) { ((BasicTask)current).setBlockingDetails("waiting for "+mutexId+":"+description); }
        
        SemaphoreWithOwners s = getSemaphore(mutexId, true);
        s.acquire();
        s.setDescription(description);
        
        if (current instanceof BasicTask) { ((BasicTask)current).setBlockingDetails(null); }
    }

    @Override
    public boolean tryAcquireMutex(String mutexId, String description) {
        SemaphoreWithOwners s = getSemaphore(mutexId, true);
        if (s.tryAcquire()) {
            s.setDescription(description);
            return true;
        }
        return false;
    }

    @Override
    public synchronized void releaseMutex(String mutexId) {
        SemaphoreWithOwners s;
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