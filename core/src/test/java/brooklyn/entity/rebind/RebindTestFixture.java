package brooklyn.entity.rebind;

import java.io.File;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.time.Duration;

import com.google.common.io.Files;

public abstract class RebindTestFixture<T extends StartableApplication> {

    protected static final Duration TIMEOUT_MS = Duration.TEN_SECONDS;

    protected ClassLoader classLoader = getClass().getClassLoader();
    protected LocalManagementContext origManagementContext;
    protected File mementoDir;
    
    protected T origApp;
    protected T newApp;
    protected ManagementContext newManagementContext;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        mementoDir = Files.createTempDir();
        origManagementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader, 1);
        origApp = createApp();
    }
    
    /** optionally, create the app as part of every test; can be no-op if tests wish to set origApp themselves */
    protected abstract T createApp();

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (origApp != null) Entities.destroyAll(origApp.getManagementContext());
        if (newApp != null) Entities.destroyAll(newApp.getManagementContext());
        origApp = null;
        newApp = null;
        newManagementContext = null;

        if (origManagementContext != null) Entities.destroyAll(origManagementContext);
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
        origManagementContext = null;
    }

    /** rebinds, and sets newApp */
    protected T rebind() throws Exception {
        if (newApp!=null || newManagementContext!=null) throw new IllegalStateException("already rebinded");
        newApp = rebind(true);
        newManagementContext = newApp.getManagementContext();
        return newApp;
    }
    
    @SuppressWarnings("unchecked")
    protected T rebind(boolean checkSerializable) throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        if (checkSerializable) {
            RebindTestUtils.checkCurrentMementoSerializable(origApp);
        }
        return (T) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }

}
