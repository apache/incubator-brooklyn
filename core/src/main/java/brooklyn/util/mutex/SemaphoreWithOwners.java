package brooklyn.util.mutex;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableList;

/** a subclass of Semaphore which requires the same thread to release as created it,
 * and which tracks who created and released the semaphores */
public class SemaphoreWithOwners extends Semaphore {
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
    
    /** Indicate that the calling thread is going to acquire or tryAcquire, 
     * in order to set up the semaphore's isInUse() value appropriately for certain checks.
     * It *must* do so after invoking this method. */ 
    public void indicateCallingThreadWillRequest() {
        requestingThreads.add(Thread.currentThread());
    }
    
}