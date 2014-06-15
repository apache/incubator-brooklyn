package brooklyn.entity.rebind.persister;

import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.time.Duration;

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
            lock.readLock().lockInterruptibly();
            lock.readLock().unlock();
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
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
