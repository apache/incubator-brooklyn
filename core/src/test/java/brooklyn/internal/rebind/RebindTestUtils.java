package brooklyn.internal.rebind;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.rebind.dto.MementosGenerators;
import brooklyn.internal.storage.DataGrid;
import brooklyn.management.internal.AbstractManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.util.javalang.Serializers;

public class RebindTestUtils {

    private static final Logger LOG = LoggerFactory.getLogger(RebindTestUtils.class);

    private static final long TIMEOUT_MS = 20*1000;
    
    @SuppressWarnings("unchecked")
	public static <T> T serializeAndDeserialize(T memento) throws Exception {
    	try {
    	    return Serializers.reconstitute(memento);
    	} catch (Exception e) {
    	    try {
    	        Dumpers.logUnserializableChains(memento);
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
        serializeAndDeserialize(memento);
    }

    public static LocalManagementContext newPersistingManagementContext(DataGrid datagrid, ClassLoader classLoader) {
        return new LocalManagementContext(datagrid);
    }

    public static Application rebind(LocalManagementContext newManagementContext, ClassLoader classLoader) throws Exception {
        LOG.info("Rebinding management context "+newManagementContext);
        
        List<Application> newApps = newManagementContext.getRebindFromDatagridManager().rebind(classLoader);
        return newApps.get(0);
    }
    
    public static Application rebind(DataGrid datagrid, ClassLoader classLoader) throws Exception {
        LOG.info("Rebinding app, using datagrid "+datagrid);
        
        LocalManagementContext newManagementContext = newPersistingManagementContext(datagrid, classLoader);
        List<Application> newApps = newManagementContext.getRebindFromDatagridManager().rebind(classLoader);
        return newApps.get(0);
    }

    public static void waitForPersisted(Application origApp) throws InterruptedException, TimeoutException {
        ((AbstractManagementContext)origApp.getManagementContext()).getRebindFromDatagridManager().waitForPendingComplete(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
    
    public static void checkCurrentMementoSerializable(Application app) throws Exception {
        // TODO Check everything in datagrid is serializable
    }
}
