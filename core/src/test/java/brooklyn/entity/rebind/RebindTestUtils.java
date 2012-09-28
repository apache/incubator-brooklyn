package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;

import java.util.List;

import brooklyn.entity.Application;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.util.Serializers;

public class RebindTestUtils {

    // Serialize, and de-serialize with a different management context
    public static Application serializeAndRebind(Application app, ClassLoader classLoader) throws Exception {
    	BrooklynMemento memento = serialize(app);
    	return rebind(memento, classLoader);
    }

    public static BrooklynMemento serialize(Application app) throws Exception {
    	return serializeAndDeserialize(app.getManagementContext().getRebindManager().getMemento());
    }
    
    public static Application rebind(BrooklynMemento memento, ClassLoader classLoader) throws Exception {
        LocalManagementContext managementContext = new LocalManagementContext();
    	List<Application> newApps = managementContext.getRebindManager().rebind(memento, classLoader);
        assertEquals(newApps.size(), 1, "apps="+newApps);
        return newApps.get(0);
    }
    
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
}
