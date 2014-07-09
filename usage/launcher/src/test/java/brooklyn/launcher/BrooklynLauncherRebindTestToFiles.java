package brooklyn.launcher;

import static org.testng.Assert.assertEquals;

import java.io.File;

import org.testng.annotations.Test;

import brooklyn.config.BrooklynServerConfig;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.persister.FileBasedObjectStore;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.os.Os;
import brooklyn.util.text.Identifiers;

import com.google.common.io.Files;

public class BrooklynLauncherRebindTestToFiles extends BrooklynLauncherRebindTestFixture {

    protected String newTempPersistenceContainerName() {
        File persistenceDirF = Files.createTempDir();
        Os.deleteOnExitRecursively(persistenceDirF);
        return persistenceDirF.getAbsolutePath();
    }
    
    protected String badContainerName() {
        return "/path/does/not/exist/"+Identifiers.makeRandomId(4);
    }
    
    protected void checkPersistenceContainerNameIs(String expected) {
        assertEquals(getPersistenceDir(lastMgmt()).getAbsolutePath(), expected);
    }

    static File getPersistenceDir(ManagementContext managementContext) {
        BrooklynMementoPersisterToObjectStore persister = (BrooklynMementoPersisterToObjectStore)managementContext.getRebindManager().getPersister();
        FileBasedObjectStore store = (FileBasedObjectStore)persister.getObjectStore();
        return store.getBaseDir();
    }

    protected void checkPersistenceContainerNameIsDefault() {
        checkPersistenceContainerNameIs(BrooklynServerConfig.DEFAULT_PERSISTENCE_DIR_FOR_FILESYSTEM);
    }

    @Test
    public void testPersistenceFailsIfIsFile() throws Exception {
        File tempF = File.createTempFile("test-"+JavaClassNames.niceClassAndMethod(), ".not_dir");
        tempF.deleteOnExit();
        String tempFileName = tempF.getAbsolutePath();
        
        try {
            runRebindFails(PersistMode.AUTO, tempFileName, "not a directory");
            runRebindFails(PersistMode.REBIND, tempFileName, "not a directory");
            runRebindFails(PersistMode.CLEAN, tempFileName, "not a directory");
        } finally {
            new File(tempFileName).delete();
        }
    }
    
    @Test
    public void testPersistenceFailsIfNotWritable() throws Exception {
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class);
        populatePersistenceDir(persistenceDir, appSpec);
        new File(persistenceDir).setWritable(false);
        try {
            runRebindFails(PersistMode.AUTO, persistenceDir, "not writable");
            runRebindFails(PersistMode.REBIND, persistenceDir, "not writable");
            runRebindFails(PersistMode.CLEAN, persistenceDir, "not writable");
        } finally {
            new File(persistenceDir).setWritable(true);
        }
    }

    @Test
    public void testPersistenceFailsIfNotReadable() throws Exception {
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class);
        populatePersistenceDir(persistenceDir, appSpec);
        new File(persistenceDir).setReadable(false);
        try {
            runRebindFails(PersistMode.AUTO, persistenceDir, "not readable");
            runRebindFails(PersistMode.REBIND, persistenceDir, "not readable");
            runRebindFails(PersistMode.CLEAN, persistenceDir, "not readable");
        } finally {
            new File(persistenceDir).setReadable(true);
        }
    }

}
