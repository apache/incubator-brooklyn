/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.util.mutex;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableList;

/** a subclass of {@link Semaphore} 
 * which tracks who created and released the semaphores,
 * and which requires the same thread to release as created it. */
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

    public void acquireUnchecked() {
        try {
            acquire();
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
    }
    public void acquireUnchecked(int numPermits) {
        try {
            acquire(numPermits);
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
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

    /** invoked when a caller successfully acquires a mutex, before {@link #onRequestFinished()} */
    protected synchronized void onAcquired(int permits) {
        for (int i=0; i<permits; i++) owningThreads.add(Thread.currentThread());
    }
    /** invoked when a caller is about to request a semaphore (before it might block);
     * guaranteed to call {@link #onRequestFinished()} after the blocking,
     * with a call to {@link #onAcquired(int)} beforehand if the acquisition was successful */
    protected synchronized void onRequesting() {
        requestingThreads.add(Thread.currentThread());
    }
    /** invoked when a caller has completed requesting a mutex, whether successful, aborted, or interrupted */
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

    /** invoked when a caller has released permits */
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