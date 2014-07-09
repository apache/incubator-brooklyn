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
package brooklyn.entity.rebind.persister;

import java.util.Comparator;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessor;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.time.Duration;

/** Wraps access to an object (the delegate {@link StoreObjectAccessor} 
 * in a guarded read-write context such that callers will be blocked if another thread
 * is accessing the object in an incompatible way (e.g. trying to read when someone is writing).
 * See {@link ReadWriteLock}.
 * <p>
 * This has no visibility or control over other access to the delegate or underlying object, of course.
 * It can only affect callers coming through this wrapper instance.  Thus callers must share instances
 * of this class for a given item.
 * <p>
 * No locking is done with respect to {@link #getLastModifiedDate()}. 
 **/
public class StoreObjectAccessorLocking implements PersistenceObjectStore.StoreObjectAccessorWithLock {

    protected static class ThreadComparator implements Comparator<Thread> {
        @Override
        public int compare(Thread o1, Thread o2) {
            if (o1.getId()<o2.getId()) return -1;
            if (o1.getId()>o2.getId()) return 1;
            return 0;
        }
    }
    
    ReadWriteLock lock = new ReentrantReadWriteLock(true);
    Set<Thread> queuedReaders = new ConcurrentSkipListSet<Thread>(new ThreadComparator());
    Set<Thread> queuedWriters = new ConcurrentSkipListSet<Thread>(new ThreadComparator());
    
    final PersistenceObjectStore.StoreObjectAccessor delegate;
    
    public StoreObjectAccessorLocking(PersistenceObjectStore.StoreObjectAccessor delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public String get() {
        try {
            queuedReaders.add(Thread.currentThread());
            lock.readLock().lockInterruptibly();
            try {
                return delegate.get();
                
            } finally {
                lock.readLock().unlock();
            }
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } finally {
            queuedReaders.remove(Thread.currentThread());
        }
    }

    @Override
    public boolean exists() {
        try {
            queuedReaders.add(Thread.currentThread());
            lock.readLock().lockInterruptibly();
            try {
                return delegate.exists();
                
            } finally {
                lock.readLock().unlock();
            }
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } finally {
            queuedReaders.remove(Thread.currentThread());
        }
    }

    protected boolean hasScheduledPutOrDeleteWithNoRead() {
        // skip write if there is another write queued and no reader waiting
        return (!queuedWriters.isEmpty() && queuedReaders.isEmpty());
    }
    
    @Override
    public void put(String val) {
        try {
            queuedWriters.add(Thread.currentThread());
            lock.writeLock().lockInterruptibly();
            try {
                queuedWriters.remove(Thread.currentThread());
                if (hasScheduledPutOrDeleteWithNoRead()) 
                    // don't bother writing if someone will write after us and no one is reading
                    return;
                delegate.put(val);
                
            } finally {
                lock.writeLock().unlock();
            }
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } finally {
            queuedWriters.remove(Thread.currentThread());
        }
    }
    
    @Override
    public void append(String val) {
        try {
            lock.writeLock().lockInterruptibly();
            try {
                if (hasScheduledPutOrDeleteWithNoRead())
                    // don't bother appending if someone will write after us and no one is reading
                    return;
                
                delegate.append(val);
                
            } finally {
                lock.writeLock().unlock();
            }
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
    }
    
    @Override
    public void delete() {
        try {
            queuedWriters.add(Thread.currentThread());
            lock.writeLock().lockInterruptibly();
            try {
                queuedWriters.remove(Thread.currentThread());
                if (hasScheduledPutOrDeleteWithNoRead()) 
                    // don't bother deleting if someone will write after us and no one is reading
                    return;
                delegate.delete();
                
            } finally {
                lock.writeLock().unlock();
            }
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } finally {
            queuedWriters.remove(Thread.currentThread());
        }
    }
    
    @Override
    public void waitForCurrentWrites(Duration timeout) throws InterruptedException, TimeoutException {
        try {
            boolean locked = lock.readLock().tryLock(timeout.toMillisecondsRoundingUp(), TimeUnit.MILLISECONDS);
            if (locked) {
                lock.readLock().unlock();
            } else {
                throw new TimeoutException("Timeout waiting for writes of "+delegate+" after "+timeout);
            }
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
    }
    
    @Override
    public Date getLastModifiedDate() {
        return delegate.getLastModifiedDate();
    }

    @Override
    public ReadWriteLock getLockObject() {
        return lock;
    }

    @Override
    public String toString() {
        return JavaClassNames.simpleClassName(this)+":"+delegate.toString();
    }
}
