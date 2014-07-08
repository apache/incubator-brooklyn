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
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.base.Stopwatch;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

/**
 * For asynchronously writing to a file.
 * 
 * This class is thread-safe. If a write is in progress, one will be scheduled. If a write is already 
 * scheduled, we will just rely on the existing one; otherwise we will write now.
 * 
 * @author aled
 * @deprecated since 0.7.0; used only by now-deprecated {@link BrooklynMementoPersisterToMultiFile}, 
 * impl largely moved to {@link FileBasedStoreObjectAccessor}
 */
@Deprecated
public class MementoFileWriter<T> {

    private static final Logger LOG = LoggerFactory.getLogger(MementoFileWriter.class);

    private final File file;
    private final File tmpFile;
    private final ListeningExecutorService executor;
    private final MementoSerializer<? super T> serializer;
    private final AtomicBoolean executing = new AtomicBoolean();
    private final AtomicReference<T> requireWrite = new AtomicReference<T>();
    private final AtomicBoolean requireDelete = new AtomicBoolean();
    private final AtomicBoolean deleted = new AtomicBoolean();
    private final AtomicLong modCount = new AtomicLong();
    
    /**
     * @param file
     * @param executor A sequential executor (e.g. SingleThreadedExecutor, or equivalent)
     * @param serializer
     */
    public MementoFileWriter(File file, ListeningExecutorService executor, MementoSerializer<? super T> serializer) {
        this.file = file;
        this.executor = executor;
        this.serializer = serializer;
        this.tmpFile = new File(file.getParentFile(), file.getName()+".tmp");
    }

    public void write(T val) {
        requireWrite.set(val);
        if (requireDelete.get() || deleted.get()) {
            LOG.warn("Not writing {}, because already deleted", file);
        } else if (executing.compareAndSet(false, true)) {
            if (LOG.isTraceEnabled()) LOG.trace("Submitting write task for {}", file);
            writeAsync();
        } else {
            if (LOG.isTraceEnabled()) LOG.trace("Execution already in-progress for {}; recorded write-requirement; returning", file);
        }
    }

    public void delete() {
        if (deleted.get() || requireDelete.get()) {
            if (LOG.isDebugEnabled()) LOG.debug("Duplicate call to delete {}; ignoring", file);
            return;
        }
        
        requireWrite.set(null);
        requireDelete.set(true);
        if (executing.compareAndSet(false, true)) {
            if (LOG.isTraceEnabled()) LOG.trace("Submitting delete task for {}", file);
            deleteAsync();
        } else {
            if (LOG.isTraceEnabled()) LOG.trace("Execution already in-progress for {}; recorded delete-requirement; returning", file);
        }
    }
    
    /**
     * This method must only be used for testing. If required in production, then revisit implementation!
     */
    @VisibleForTesting
    public void waitForWriteCompleted(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        waitForWriteCompleted(Duration.of(timeout, unit));
    }
    
    @VisibleForTesting
    public void waitForWriteCompleted(Duration timeout) throws InterruptedException, TimeoutException {
        // Every time we finish writing, we increment a counter. We note the current val, and then
        // wait until we can guarantee that a complete additional write has been done. Not sufficient
        // to wait for `writeCount > origWriteCount` because we might have read the value when it was 
        // almost finished a write.
        
        long timeoutMillis = timeout.toMilliseconds();
        long startTime = System.currentTimeMillis();
        long maxEndtime = (timeoutMillis > 0) ? (startTime + timeoutMillis) : (timeoutMillis < 0) ? startTime : Long.MAX_VALUE;
        long origModCount = modCount.get();
        while (true) {
            if (modCount.get() > (origModCount+1)) {
                return;
            } else if (requireWrite.get() != null) {
                // must continue waiting for mods+1
            } else if (executing.get()) {
                // must wait for either this invocation to complete, or mods+1 (because might have already updated)
            } else {
                return;
            }
            
            if (System.currentTimeMillis() > maxEndtime) {
                throw new TimeoutException("Timeout waiting for pending complete of rebind-periodic-delta, after "+Time.makeTimeStringRounded(timeout));
            }
            Thread.sleep(10);
        }
    }

    private void deleteAsync() {
        ListenableFuture<Void> future = executor.submit(new Callable<Void>() {
            @Override public Void call() throws IOException {
                try {
                    deleteNow();
                    return null;
                } catch (Throwable t) {
                    if (executor.isShutdown()) {
                        LOG.debug("Error deleting "+file+" (but executor shutdown)", t);
                        return null; // just return without throwing; no more work to do
                    } else {
                        LOG.error("Error deleting "+file, t);
                        throw Exceptions.propagate(t);
                    }
                }
            }});
        addPostExecListener(future);
    }

    private void writeAsync() {
        ListenableFuture<Void> future = executor.submit(new Callable<Void>() {
            @Override public Void call() throws IOException {
                try {
                    writeNow();
                    return null;
                } catch (Throwable t) {
                    if (executor.isShutdown()) {
                        LOG.debug("Error writing to "+file+" (but executor shutdown)", t);
                        return null; // just return without throwing; no more work to do
                    } else {
                        LOG.error("Error writing to "+file, t);
                        throw Exceptions.propagate(t);
                    }
                }
             }});
        addPostExecListener(future);
    }
    
    private void addPostExecListener(ListenableFuture<?> future) {
        future.addListener(
                new Runnable() {
                    @Override public void run() {
                        if (LOG.isTraceEnabled()) LOG.trace("Write complete for {}", file);
                        try {
                            executing.set(false);
                            if (requireDelete.get()) {
                                if (executing.compareAndSet(false, true)) {
                                    if (LOG.isTraceEnabled()) LOG.trace("Submitting delete-task for {} (in post-exec) due to recorded delete-requirement", file);
                                    deleteAsync();
                                } else {
                                    if (LOG.isTraceEnabled()) LOG.trace("Delete-requirement for {} (in post-exec) handled by other thread; returning", file);
                                }
                                
                            } else if (requireWrite.get() != null) {
                                if (executing.compareAndSet(false, true)) {
                                    if (LOG.isTraceEnabled()) LOG.trace("Submitting write task for {} (in post-exec) due to recorded write-requirement", file);
                                    writeAsync();
                                } else {
                                    if (LOG.isTraceEnabled()) LOG.trace("Write-requirement for {} (in post-exec) handled by other thread; returning", file);
                                }
                            } else {
                                if (LOG.isTraceEnabled()) LOG.trace("No pending exec-requirements for {}", file);
                            }
                        } catch (Throwable t) {
                            if (executor.isShutdown()) {
                                LOG.debug("Error in post-exec for "+file+" (but executor shutdown)", t);
                                return; // just return without throwing; no more work to do
                            } else {
                                LOG.error("Error in post-exec for "+file, t);
                                throw Exceptions.propagate(t);
                            }
                        }
                    }
                }, 
                MoreExecutors.sameThreadExecutor());
    }
    
    private void writeNow() throws IOException {
        T val = requireWrite.getAndSet(null);
        
        /*
         * Need to guarantee "happens before", with any thread that has written 
         * fields of these mementos. In particular, saw failures where SshMachineLocation
         * had null address field. Our hypothesis is that another thread wrote the memento,
         * but that no synchronization subsequently happened so we did not see all the values
         * in that memento from this thread.
         * 
         * See PeriodicDeltaChangeListener.persistNow for the corresponding synchronization,
         * that guarantees its thread made the writes visible.
         */
        synchronized (new Object()) {}

        Stopwatch stopwatch = Stopwatch.createStarted();
        
        // Write to the temp file, then atomically move it to the permanent file location
        Files.write(serializer.toString(val), tmpFile, Charsets.UTF_8);
        Files.move(tmpFile, file);

        modCount.incrementAndGet();

        if (LOG.isTraceEnabled()) LOG.trace("Wrote {}, took {}; modified file {} times", 
                new Object[] {file, Time.makeTimeStringRounded(stopwatch), modCount});
    }
    
    private void deleteNow() throws IOException {
        if (LOG.isTraceEnabled()) LOG.trace("Deleting {} and {}", file, tmpFile);
        deleted.set(true);
        requireDelete.set(false);
        
        file.delete();
        tmpFile.delete();
        
        modCount.incrementAndGet();
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("file", file).toString();
    }
}
