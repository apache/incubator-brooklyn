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

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.base.Stopwatch;
import com.google.common.io.Files;

/**
 * For synchronously writing to a file - all calls block.
 * 
 * This class is thread-safe. If a write/delete/append is in progress, then subsequent calls will 
 * block.
 * 
 * @author aled
 * @deprecated since 0.7.0 we use {@link PersistenceObjectStore} instances now, and when we need sync behaviour we just wait
 */
@Deprecated
public class MementoFileWriterSync<T> {

    private static final Logger LOG = LoggerFactory.getLogger(MementoFileWriterSync.class);

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final File file;
    private final File tmpFile;
    private final MementoSerializer<? super T> serializer;
    private final AtomicLong modCount = new AtomicLong();
    
    /**
     * @param file
     * @param serializer
     */
    public MementoFileWriterSync(File file, MementoSerializer<? super T> serializer) {
        this(file, serializer, "tmp");
    }
    
    public MementoFileWriterSync(File file, MementoSerializer<? super T> serializer, String tmpFileSuffix) {
        this.file = file;
        this.serializer = serializer;
        this.tmpFile = new File(file.getParentFile(), file.getName()+"."+tmpFileSuffix);
    }

    public boolean exists() {
        return file.exists();
    }
    
    public void write(T val) {
        try {
            lock.writeLock().lockInterruptibly();
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
        try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            
            // Write to the temp file, then atomically move it to the permanent file location
            Files.write(serializer.toString(val), tmpFile, Charsets.UTF_8);
            Files.move(tmpFile, file);
            modCount.incrementAndGet();

            if (LOG.isTraceEnabled()) LOG.trace("Wrote {}, took {}; modified file {} times", 
                    new Object[] {file, Time.makeTimeStringRounded(stopwatch), modCount});
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void append(T val) {
        try {
            lock.writeLock().lockInterruptibly();
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
        try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            
            // Write to the temp file, then atomically move it to the permanent file location
            Files.append(serializer.toString(val), file, Charsets.UTF_8);
            modCount.incrementAndGet();

            if (LOG.isTraceEnabled()) LOG.trace("Wrote {}, took {}; modified file {} times", 
                    new Object[] {file, Time.makeTimeStringRounded(stopwatch), modCount});
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void delete() {
        try {
            lock.writeLock().lockInterruptibly();
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
        try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            
            file.delete();
            tmpFile.delete();
            modCount.incrementAndGet();
            
            if (LOG.isTraceEnabled()) LOG.trace("Deleted {}, took {}; modified file {} times", 
                    new Object[] {file, Time.makeTimeStringRounded(stopwatch), modCount});
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @VisibleForTesting
    public void waitForWriteCompleted(Duration timeout) throws InterruptedException, TimeoutException {
        boolean locked = lock.writeLock().tryLock(timeout.toMilliseconds(), TimeUnit.MILLISECONDS);
        if (locked) {
            lock.writeLock().unlock();
        } else {
            throw new TimeoutException("Timeout waiting for lock on "+file);
        }
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("file", file).toString();
    }
}
