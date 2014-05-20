package brooklyn.entity.rebind;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.collections.Maps;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.rebind.Dumpers.Pointer;
import brooklyn.entity.rebind.dto.MementosGenerators;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToMultiFile;
import brooklyn.entity.trait.Identifiable;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.util.javalang.Serializers;
import brooklyn.util.javalang.Serializers.ObjectReplacer;
import brooklyn.util.time.Duration;

public class RebindTestUtils {

    private static final Logger LOG = LoggerFactory.getLogger(RebindTestUtils.class);

    private static final Duration TIMEOUT = Duration.seconds(20);
    
	public static <T> T serializeAndDeserialize(T memento) throws Exception {
        ObjectReplacer replacer = new ObjectReplacer() {
            private final Map<Pointer, Object> replaced = Maps.newLinkedHashMap();
            
            @Override public Object replace(Object toserialize) {
                if (toserialize instanceof Location || toserialize instanceof Entity) {
                    Pointer pointer = new Pointer(((Identifiable)toserialize).getId());
                    replaced.put(pointer, toserialize);
                    return pointer;
                }
                return toserialize;
            }
            @Override public Object resolve(Object todeserialize) {
                if (todeserialize instanceof Pointer) {
                    return checkNotNull(replaced.get(todeserialize), todeserialize);
                }
                return todeserialize;
            }
        };

    	try {
    	    return Serializers.reconstitute(memento, replacer);
    	} catch (Exception e) {
    	    try {
    	        Dumpers.logUnserializableChains(memento, replacer);
    	        //Dumpers.deepDumpSerializableness(memento);
    	    } catch (Throwable t) {
    	        LOG.warn("Error logging unserializable chains for memento "+memento+" (propagating original exception)", t);
    	    }
    	    throw e;
    	}
    }
    
    public static void deleteMementoDir(File f) {
        if (f.isDirectory()) {
          for (File c : f.listFiles())
            deleteMementoDir(c);
        }
        f.delete();
    }
    
    public static void checkMementoSerializable(Application app) throws Exception {
        BrooklynMemento memento = MementosGenerators.newBrooklynMemento(app.getManagementContext());
        checkMementoSerializable(memento);
    }

    public static void checkMementoSerializable(BrooklynMemento memento) throws Exception {
        serializeAndDeserialize(memento);
    }
    
    public static LocalManagementContext newPersistingManagementContext(File mementoDir, ClassLoader classLoader) {
        return managementContextBuilder(mementoDir, classLoader).buildStarted();
    }

    public static LocalManagementContext newPersistingManagementContext(File mementoDir, ClassLoader classLoader, long persistPeriodMillis) {
        return managementContextBuilder(mementoDir, classLoader)
                .persistPeriodMillis(persistPeriodMillis)
                .buildStarted();
    }

    public static LocalManagementContext newPersistingManagementContextUnstarted(File mementoDir, ClassLoader classLoader) {
        return managementContextBuilder(mementoDir, classLoader).buildUnstarted();
    }
    
    public static ManagementContextBuilder managementContextBuilder(File mementoDir, ClassLoader classLoader) {
        return new ManagementContextBuilder(mementoDir, classLoader);
    }

    public static class ManagementContextBuilder {
        final File mementoDir;
        final ClassLoader classLoader;
        long persistPeriodMillis = 100;
        BrooklynProperties properties;

        ManagementContextBuilder(File mementoDir, ClassLoader classLoader) {
            this.mementoDir = checkNotNull(mementoDir, "mementoDir");
            this.classLoader = checkNotNull(classLoader, "classLoader");
        }

        public ManagementContextBuilder persistPeriodMillis(long persistPeriodMillis) {
            checkArgument(persistPeriodMillis > 0, "persistPeriodMillis must be greater than 0; was "+persistPeriodMillis);
            this.persistPeriodMillis = persistPeriodMillis;
            return this;
        }

        public ManagementContextBuilder properties(BrooklynProperties properties) {
            this.properties = checkNotNull(properties, "properties");
            return this;
        }

        public LocalManagementContext buildUnstarted() {
            LocalManagementContext unstarted;
            if (properties != null) {
                unstarted = new LocalManagementContext(properties);
            } else {
                unstarted = new LocalManagementContext();
            }
            BrooklynMementoPersisterToMultiFile newPersister = new BrooklynMementoPersisterToMultiFile(mementoDir, classLoader);
            ((RebindManagerImpl) unstarted.getRebindManager()).setPeriodicPersistPeriod(Duration.of(persistPeriodMillis, TimeUnit.MILLISECONDS));
            unstarted.getRebindManager().setPersister(newPersister);
            return unstarted;
        }

        public LocalManagementContext buildStarted() {
            LocalManagementContext unstarted = buildUnstarted();
            unstarted.getHighAvailabilityManager().disabled();
            unstarted.getRebindManager().start();
            return unstarted;
        }

    }

    public static Application rebind(File mementoDir, ClassLoader classLoader) throws Exception {
        return rebind(mementoDir, classLoader, (RebindExceptionHandler)null);
    }
    
    public static Application rebind(File mementoDir, ClassLoader classLoader, RebindExceptionHandler exceptionHandler) throws Exception {
        LOG.info("Rebinding app, using directory "+mementoDir);
        
        LocalManagementContext newManagementContext = newPersistingManagementContextUnstarted(mementoDir, classLoader);
        List<Application> newApps;
        if (exceptionHandler == null) {
            newApps = newManagementContext.getRebindManager().rebind(classLoader);
        } else {
            newApps = newManagementContext.getRebindManager().rebind(classLoader, exceptionHandler);
        }
        newManagementContext.getRebindManager().start();
        if (newApps.isEmpty()) throw new IllegalStateException("Application could not be rebinded; serialization probably failed");
        return newApps.get(0);
    }

    public static Application rebind(ManagementContext newManagementContext, File mementoDir, ClassLoader classLoader) throws Exception {
        return rebind(newManagementContext, mementoDir, classLoader, (RebindExceptionHandler)null);
    }
    
    public static Application rebind(ManagementContext newManagementContext, File mementoDir, ClassLoader classLoader, RebindExceptionHandler exceptionHandler) throws Exception {
        LOG.info("Rebinding app, using directory "+mementoDir);
        
        BrooklynMementoPersisterToMultiFile newPersister = new BrooklynMementoPersisterToMultiFile(mementoDir, classLoader);
        newManagementContext.getRebindManager().setPersister(newPersister);
        List<Application> newApps;
        if (exceptionHandler == null) {
            newApps = newManagementContext.getRebindManager().rebind(classLoader);
        } else {
            newApps = newManagementContext.getRebindManager().rebind(classLoader, exceptionHandler);
        }
        newManagementContext.getRebindManager().start();
        return newApps.get(0);
    }

    public static void waitForPersisted(Application origApp) throws InterruptedException, TimeoutException {
        waitForPersisted(origApp.getManagementContext());
    }
    
    public static void waitForPersisted(ManagementContext managementContext) throws InterruptedException, TimeoutException {
        managementContext.getRebindManager().waitForPendingComplete(TIMEOUT);
    }
    
    public static void checkCurrentMementoSerializable(Application app) throws Exception {
        BrooklynMemento memento = MementosGenerators.newBrooklynMemento(app.getManagementContext());
        serializeAndDeserialize(memento);
    }
}
