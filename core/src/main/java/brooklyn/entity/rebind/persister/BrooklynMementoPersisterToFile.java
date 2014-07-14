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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.rebind.PersistenceExceptionHandler;
import brooklyn.entity.rebind.RebindExceptionHandler;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.google.common.io.Files;

/**
 * @deprecated since 0.7.0 use BrooklynMementoPersisterToMultiFile instead; the single-file version
 *             has not been tested recently or kept up-to-date. 
 */
@Deprecated
public class BrooklynMementoPersisterToFile extends AbstractBrooklynMementoPersister {

    // FIXME This is no longer used (instead we use ToMultiFile).
    // Is this definitely no longer useful? Delete if not, and 
    // merge AbstractBrooklynMementoPersister+BrooklynMementoPerisisterInMemory.

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynMementoPersisterToFile.class);

    private final File file;
    private final MementoSerializer<BrooklynMemento> serializer;
    private final Object mutex = new Object();

    
    public BrooklynMementoPersisterToFile(File file, ClassLoader classLoader) {
        this.file = file;
        this.serializer = new XmlMementoSerializer<BrooklynMemento>(classLoader);
    }
    
    @VisibleForTesting
    @Override
    public void waitForWritesCompleted(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
    }
    
    @VisibleForTesting
    @Override
    public void waitForWritesCompleted(Duration timeout) throws InterruptedException, TimeoutException {
        // TODO Could wait for concurrent checkpoint/delta, but don't need to for tests
        // because they first wait for checkpoint/delta to have been called by RebindManagerImpl.
        return;
    }

    @Override
    public BrooklynMemento loadMemento(LookupContext lookupContext, RebindExceptionHandler exceptionHandler) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        
        String xml = readFile();
        serializer.setLookupContext(lookupContext);
        try {
            BrooklynMemento result = serializer.fromString(xml);
            
            if (LOG.isDebugEnabled()) LOG.debug("Loaded memento; took {}", Time.makeTimeStringRounded(stopwatch));
            return result;
            
        } finally {
            serializer.unsetLookupContext();
        }
    }
    
    private String readFile() {
        try {
            synchronized (mutex) {
                return Files.asCharSource(file, Charsets.UTF_8).read();
            }
        } catch (IOException e) {
            LOG.error("Failed to persist memento", e);
            throw Exceptions.propagate(e);
        }
    }
    
    @Override
    public void checkpoint(BrooklynMemento newMemento, PersistenceExceptionHandler exceptionHandler) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        synchronized (mutex) {
            long timeObtainedMutex = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            super.checkpoint(newMemento, exceptionHandler);
            long timeCheckpointed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            writeMemento();
            long timeWritten = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            
            if (LOG.isDebugEnabled()) LOG.debug("Checkpointed memento; total={}ms, obtainingMutex={}ms, " +
                    "checkpointing={}ms, writing={}ms", 
                    new Object[] {timeWritten, timeObtainedMutex, (timeCheckpointed-timeObtainedMutex), 
                    (timeWritten-timeCheckpointed)});
        }
    }
    
    @Override
    public void delta(Delta delta, PersistenceExceptionHandler exceptionHandler) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        synchronized (mutex) {
            long timeObtainedMutex = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            super.delta(delta, exceptionHandler);
            long timeDeltad = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            writeMemento();
            long timeWritten = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            
            if (LOG.isDebugEnabled()) LOG.debug("Checkpointed memento; total={}ms, obtainingMutex={}ms, " +
                    "delta'ing={}ms, writing={}", 
                    new Object[] {timeWritten, timeObtainedMutex, (timeDeltad-timeObtainedMutex), 
                    (timeWritten-timeDeltad)});
        }
    }
    
    private void writeMemento() {
        assert Thread.holdsLock(mutex);
        try {
            Files.write(serializer.toString(memento), file, Charsets.UTF_8);
        } catch (IOException e) {
            LOG.error("Failed to persist memento", e);
        }
    }
}
