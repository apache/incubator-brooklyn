package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import brooklyn.entity.Application;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.mementos.BrooklynMemento;

public class RebindTestUtils {

    // Serialize, and de-serialize with a different management context
    public static Application serializeRebindAndManage(Application app, ClassLoader classLoader) throws Exception {
    	BrooklynMemento memento = serialize(app);
    	return rebindAndManage(memento, classLoader);
    }

    public static BrooklynMemento serialize(Application app) throws Exception {
    	return serializeAndDeserialize(app.getManagementContext().getRebindManager().getMemento());
    }
    
    public static Application rebindAndManage(BrooklynMemento memento, ClassLoader classLoader) throws Exception {
        LocalManagementContext managementContext = new LocalManagementContext();
    	List<Application> newApps = managementContext.getRebindManager().rebind(memento, classLoader);
        assertEquals(newApps.size(), 1, "apps="+newApps);
        managementContext.manage(newApps.get(0));
        return newApps.get(0);
    }
    
    @SuppressWarnings("unchecked")
	public static <T> T serializeAndDeserialize(T memento) throws Exception {
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	ObjectOutputStream oos = new ObjectOutputStream(baos);
    	oos.writeObject(memento);
    	oos.close();
    	
    	ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    	ObjectInputStream ois = new ObjectInputStream(bais);
    	try {
    		return (T) ois.readObject();
    	} catch (Exception e) {
    		throw e; // FIXME just for breakpointing
    	} finally {
    		ois.close();
    	}
    }
    
    public static List<Application> rebind(BrooklynMemento memento, ClassLoader classLoader) {
        LocalManagementContext managementContext = new LocalManagementContext();
        return managementContext.getRebindManager().rebind(memento, classLoader);
    }
}
