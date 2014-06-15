package brooklyn.entity.rebind.persister;

import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.ManagementContext;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.util.text.Strings;
import brooklyn.util.time.CountdownTimer;
import brooklyn.util.time.Duration;

import com.google.common.base.Preconditions;

public class ListeningObjectStore implements PersistenceObjectStore {

    protected final PersistenceObjectStore delegate;
    protected final ObjectStoreTransactionListener listener;

    public static interface ObjectStoreTransactionListener {
        public void recordQueryOut(String summary, int size);
        public void recordDataOut(String summary, int size);
        public void recordDataIn(String summary, int size);
    }
    
    public static class RecordingTransactionListener implements ObjectStoreTransactionListener {
        private static final Logger log = LoggerFactory.getLogger(ListeningObjectStore.RecordingTransactionListener.class);
        
        protected final String prefix;
        protected final AtomicLong bytesIn = new AtomicLong();
        protected final AtomicLong bytesOut = new AtomicLong();
        protected final AtomicInteger countQueriesOut = new AtomicInteger();
        protected final AtomicInteger countDataOut = new AtomicInteger();
        protected final AtomicInteger countDataIn = new AtomicInteger();
        
        public RecordingTransactionListener(String prefix) {
            this.prefix = prefix;
        }
        
        public long getBytesIn() {
            return bytesIn.get();
        }
        
        public long getBytesOut() {
            return bytesOut.get();
        }
        
        public int getCountQueriesOut() {
            return countQueriesOut.get();
        }
        
        public int getCountDataOut() {
            return countDataOut.get();
        }
        
        public int getCountDataIn() {
            return countDataIn.get();
        }
        
        public String getTotalString() {
            return "totals: out="+Strings.makeSizeString(bytesOut.get())+" in="+Strings.makeSizeString(bytesIn.get());
        }
        
        @Override
        public void recordQueryOut(String summary, int size) {
            synchronized (this) { this.notifyAll(); }
            bytesOut.addAndGet(size);
            countQueriesOut.incrementAndGet();
            log.info(prefix+" "+summary+" -->"+size+"; "+getTotalString());
        }
        
        @Override
        public void recordDataOut(String summary, int size) {
            synchronized (this) { this.notifyAll(); }
            bytesOut.addAndGet(size);
            countDataOut.incrementAndGet();
            log.info(prefix+" "+summary+" -->"+size+"; "+getTotalString());
        }
        
        @Override
        public void recordDataIn(String summary, int size) {
            synchronized (this) { this.notifyAll(); }
            bytesIn.addAndGet(size);
            countDataIn.incrementAndGet();
            log.info(prefix+" "+summary+" <--"+size+"; "+getTotalString());
        }

        public void blockUntilDataWrittenExceeds(long count, Duration timeout) throws InterruptedException, TimeoutException {
            CountdownTimer timer = CountdownTimer.newInstanceStarted(timeout);
            synchronized (this) {
                while (bytesOut.get()<count) {
                    if (timer.isExpired())
                        throw new TimeoutException();
                    timer.waitOnForExpiry(this);
                }
            }
        }
    }

    public ListeningObjectStore(PersistenceObjectStore delegate, ObjectStoreTransactionListener listener) {
        this.delegate = Preconditions.checkNotNull(delegate);
        this.listener = Preconditions.checkNotNull(listener);
    }

    @Override
    public String getSummaryName() {
        return delegate.getSummaryName();
    }

    @Override
    public void prepareForContendedWrite() {
        delegate.prepareForContendedWrite();
    }

    @Override
    public StoreObjectAccessor newAccessor(String path) {
        return new ListeningAccessor(path, delegate.newAccessor(path));
    }

    @Override
    public void createSubPath(String subPath) {
        listener.recordQueryOut("creating path "+subPath, 1+subPath.length());
        delegate.createSubPath(subPath);
    }

    @Override
    public List<String> listContentsWithSubPath(String subPath) {
        listener.recordQueryOut("requesting list "+subPath, 1+subPath.length());
        List<String> result = delegate.listContentsWithSubPath(subPath);
        listener.recordDataIn("receiving list "+subPath, result.toString().length());
        return result;
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public void injectManagementContext(ManagementContext managementContext) {
        delegate.injectManagementContext(managementContext);
    }
    
    @Override
    public void prepareForUse(PersistMode persistMode, HighAvailabilityMode haMode) {
        delegate.prepareForUse(persistMode, haMode);
    }

    @Override
    public void deleteCompletely() {
        listener.recordDataOut("deleting completely", 1);
        delegate.deleteCompletely();
    }

    public class ListeningAccessor implements StoreObjectAccessor {

        protected final String path;
        protected final StoreObjectAccessor delegate;
        
        public ListeningAccessor(String path, StoreObjectAccessor delegate) {
            this.path = path;
            this.delegate = delegate;
        }
        public boolean exists() {
            return delegate.exists();
        }
        public void put(String val) {
            listener.recordDataOut("writing "+path, val.length());
            delegate.put(val);
        }
        public void append(String s) {
            listener.recordDataOut("appending "+path, s.length());
            delegate.append(s);
        }
        public void delete() {
            listener.recordQueryOut("deleting "+path, path.length());
            delegate.delete();
        }
        public String get() {
            listener.recordQueryOut("requesting "+path, path.length());
            String result = delegate.get();
            listener.recordDataIn("reading "+path, result.length());
            return result;
        }
    }

}
