package brooklyn.entity.rebind;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.rebind.dto.MementosGenerators;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToMultiFile;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.util.Serializers;

public class RebindTestUtils {

    private static final Logger LOG = LoggerFactory.getLogger(RebindTestUtils.class);
    
//    // Serialize, and de-serialize with a different management context
//    public static Application serializeAndRebind(Application app, ClassLoader classLoader) throws Exception {
//    	BrooklynMemento memento = serialize(app);
//    	return rebind(memento, classLoader);
//    }
//
//    public static BrooklynMemento serialize(Application app) throws Exception {
//    	return serializeAndDeserialize(app.getManagementContext().getRebindManager().getMemento());
//    }
//    
//    public static Application rebind(BrooklynMemento memento, ClassLoader classLoader) throws Exception {
//        LocalManagementContext managementContext = new LocalManagementContext();
//    	List<Application> newApps = managementContext.getRebindManager().rebind(memento, classLoader);
//        assertEquals(newApps.size(), 1, "apps="+newApps);
//        return newApps.get(0);
//    }
    
    @SuppressWarnings("unchecked")
	public static <T> T serializeAndDeserialize(T memento) throws Exception {
    	try {
    	    return Serializers.reconstitute(memento);
    	} catch (Exception e) {
    	    try {
    	        Dumpers.logUnserializableChains(memento);
    	        //Dumpers.deepDumpSerializableness(memento);
    	    } finally {
    	        throw e;
    	    }
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
        serializeAndDeserialize(memento);
    }

    public static LocalManagementContext newPersistingManagementContext(File mementoDir, ClassLoader classLoader) {
        LocalManagementContext result = new LocalManagementContext();
        BrooklynMementoPersisterToMultiFile newPersister = new BrooklynMementoPersisterToMultiFile(mementoDir, classLoader);
        result.getRebindManager().setPersister(newPersister);
        return result;
    }
    
    public static LocalManagementContext newPersistingManagementContext(File mementoDir, ClassLoader classLoader, long persistPeriodMillis) {
        checkArgument(persistPeriodMillis > 0, "persistPeriodMillis must be greater than 0; was "+persistPeriodMillis);
        LocalManagementContext result = new LocalManagementContext();
        BrooklynMementoPersisterToMultiFile newPersister = new BrooklynMementoPersisterToMultiFile(mementoDir, classLoader);
        ((RebindManagerImpl)result.getRebindManager()).setPeriodicPersistPeriod(persistPeriodMillis);
        result.getRebindManager().setPersister(newPersister);
        return result;
    }

    public static Application rebind(File mementoDir, ClassLoader classLoader) throws Exception {
        LOG.info("Rebinding app, using directory "+mementoDir);
        
        LocalManagementContext newManagementContext = newPersistingManagementContext(mementoDir, classLoader);
        BrooklynMementoPersister newPersister = newManagementContext.getRebindManager().getPersister();
        List<Application> newApps = newManagementContext.getRebindManager().rebind(newPersister.loadMemento(), classLoader);
        return newApps.get(0);
    }

    public static Application rebind(ManagementContext newManagementContext, File mementoDir, ClassLoader classLoader) throws Exception {
        LOG.info("Rebinding app, using directory "+mementoDir);
        
        BrooklynMementoPersisterToMultiFile newPersister = new BrooklynMementoPersisterToMultiFile(mementoDir, classLoader);
        newManagementContext.getRebindManager().setPersister(newPersister);
        List<Application> newApps = newManagementContext.getRebindManager().rebind(newPersister.loadMemento(), classLoader);
        return newApps.get(0);
    }

    public static void waitForPersisted(Application origApp) throws InterruptedException {
        origApp.getManagementContext().getRebindManager().waitForPendingComplete();
    }
    
    public static void checkCurrentMementoSerializable(Application app) throws Exception {
        BrooklynMemento memento = MementosGenerators.newBrooklynMemento(app.getManagementContext());
        serializeAndDeserialize(memento);
    }
}
