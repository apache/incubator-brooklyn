package brooklyn.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import brooklyn.management.Task;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.task.BasicTask;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/** interface which allows multiple callers to co-operate using named mutexes, inspectably,
 * and containing implementation as inner class */
public interface HasMutexes {

    /** returns true if the calling thread has the mutex with the given ID */
    public boolean hasMutex(String mutexId);
    
    /** acquires a mutex, if available, otherwise blocks on its becoming available;
     * caller must release after use */
    public void acquireMutex(String mutexId, String description) throws InterruptedException;

    /** acquires a mutex and returns true, if available; otherwise immediately returns false;
     * caller must release after use if this returns true */
    public boolean tryAcquireMutex(String mutexId, String description);

    /** releases a mutex, triggering another thread to use it or cleaning it up if no one else is waiting;
     * this should only be called by the mutex owner (thread) */
    public void releaseMutex(String mutexId);

    /** a subclass of Semaphore which requires the same thread to release as created it,
     * and which tracks who created and released the semaphores */
    public static class SemaphoreWithOwners extends Semaphore {
        public SemaphoreWithOwners(String name) {
            this(name, 1, true);
        }
        public SemaphoreWithOwners(String name, int permits, boolean fair) {
            super(permits, fair);
            this.name = name;
        }
        private static final long serialVersionUID = -5303474637353009454L;
        final private List<Thread> owningThreads = new ArrayList<Thread>();
        final private Set<Thread> requestingThreads = new LinkedHashSet<Thread>();

        @Override
        public void acquire() throws InterruptedException {
            try {
                onRequesting();
                super.acquire();
                onAcquired(1);
            } finally {
                onRequestFinished();
            }
        }
        @Override
        public void acquire(int permits) throws InterruptedException {
            try {
                onRequesting();
                super.acquire(permits);
                onAcquired(permits);
            } finally {
                onRequestFinished();
            }
        }
        @Override
        public void acquireUninterruptibly() {
            try {
                onRequesting();
                super.acquireUninterruptibly();
                onAcquired(1);
            } finally {
                onRequestFinished();
            }
        }
        @Override
        public void acquireUninterruptibly(int permits) {
            try {
                onRequesting();
                super.acquireUninterruptibly(permits);
                onAcquired(permits);
            } finally {
                onRequestFinished();
            }
        }

        @Override
        public boolean tryAcquire() {
            try {
                onRequesting();
                if (super.tryAcquire()) {
                    onAcquired(1);
                    return true;
                }
                return false;
            } finally {
                onRequestFinished();
            }
        }
        @Override
        public boolean tryAcquire(int permits) {
            try {
                onRequesting();
                if (super.tryAcquire(permits)) {
                    onAcquired(permits);
                    return true;
                }
                return false;
            } finally {
                onRequestFinished();
            }
        }
        @Override
        public boolean tryAcquire(int permits, long timeout, TimeUnit unit) throws InterruptedException {
            try {
                onRequesting();
                if (super.tryAcquire(permits, timeout, unit)) {
                    onAcquired(permits);
                    return true;
                }
                return false;
            } finally {
                onRequestFinished();
            }
        }
        @Override
        public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
            try {
                onRequesting();
                if (super.tryAcquire(timeout, unit)) {
                    onAcquired(1);
                    return true;
                }
                return false;
            } finally {
                onRequestFinished();
            }
        }

        protected synchronized void onAcquired(int permits) {
            for (int i=0; i<permits; i++) owningThreads.add(Thread.currentThread());
        }
        protected synchronized void onRequesting() {
            requestingThreads.add(Thread.currentThread());
        }
        protected synchronized void onRequestFinished() {
            requestingThreads.remove(Thread.currentThread());
        }

        @Override
        public void release() {
            super.release();
            onReleased(1);
        }
        @Override
        public void release(int permits) {
            super.release(permits);
            onReleased(permits);
        }

        protected synchronized void onReleased(int permits) {
            boolean result = true;
            for (int i=0; i<permits; i++) result = owningThreads.remove(Thread.currentThread()) & result;
            if (!result) throw new IllegalStateException("Thread "+Thread.currentThread()+" which released "+this+" did not own it.");  
        }

        /** true iff there are any owners or any requesters (callers blocked trying to acquire) */
        public synchronized boolean isInUse() {
            return !owningThreads.isEmpty() || !requestingThreads.isEmpty();
        }

        /** true iff the calling thread is one of the owners */ 
        public synchronized boolean isCallingThreadAnOwner() {
            return owningThreads.contains(Thread.currentThread());
        }

        private final String name;
        /** constructor-time supplied name */
        public String getName() { return name; }

        private String description;
        public void setDescription(String description) { this.description = description; }
        /** caller supplied description */
        public String getDescription() { return description; }

        /** unmodifiable view of threads owning the permits; threads with multiple permits listed multiply */
        public synchronized List<Thread> getOwningThreads() {
            return ImmutableList.<Thread>copyOf(owningThreads);
        }
        /** unmodifiable view of threads requesting access (blocked or briefly trying to acquire);
         * this is guaranteed to be cleared _after_ getOwners 
         * (synchronizing on this class while reading both fields will give canonical access) */
        public synchronized List<Thread> getRequestingThreads() {
            return ImmutableList.<Thread>copyOf(requestingThreads);
        }
        
        @Override
        public synchronized String toString() {
            return super.toString()+"["+name+"; description="+description+"; owning="+owningThreads+"; requesting="+requestingThreads+"]";
        }
    }

    public static class MutexSupport implements HasMutexes {

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
            if (requestBeforeReturning) {
                s.requestingThreads.add(Thread.currentThread());
            }
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
    
}
