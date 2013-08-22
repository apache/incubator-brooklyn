package brooklyn.entity.rebind.persister;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.mementos.BrooklynMemento;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.io.Files;

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
        // TODO Could wait for concurrent checkpoint/delta, but don't need to for tests
        // because they first wait for checkpoint/delta to have been called by RebindManagerImpl.
        return;
    }

    @Override
    public BrooklynMemento loadMemento() {
        try {
            Stopwatch stopwatch = new Stopwatch().start();
            List<String> lines;
            synchronized (mutex) {
                lines = Files.readLines(file, Charsets.UTF_8);
            }
            String xml = Joiner.on("\n").join(lines);
            BrooklynMemento result = serializer.fromString(xml);
            
            if (LOG.isDebugEnabled()) LOG.debug("Loaded memento; total={}ms", stopwatch.elapsed(TimeUnit.MILLISECONDS)); 

            return result;
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
    
    @Override
    public void checkpoint(BrooklynMemento newMemento) {
        Stopwatch stopwatch = new Stopwatch().start();
        synchronized (mutex) {
            long timeObtainedMutex = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            super.checkpoint(newMemento);
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
    public void delta(Delta delta) {
        Stopwatch stopwatch = new Stopwatch().start();
        synchronized (mutex) {
            long timeObtainedMutex = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            super.delta(delta);
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
        try {
            Files.write(serializer.toString(memento), file, Charsets.UTF_8);
        } catch (IOException e) {
            LOG.error("Failed to persist memento", e);
        }
    }
}
