package brooklyn.entity.rebind;

import java.io.File;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToMultiFile;
import brooklyn.internal.BrooklynFeatureEnablement;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.mementos.BrooklynMementoManifest;
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

    private boolean origPolicyPersistenceEnabled;
    private boolean origEnricherPersistenceEnabled;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        origPolicyPersistenceEnabled = BrooklynFeatureEnablement.enable(BrooklynFeatureEnablement.FEATURE_POLICY_PERSISTENCE_PROPERTY);
        origEnricherPersistenceEnabled = BrooklynFeatureEnablement.enable(BrooklynFeatureEnablement.FEATURE_ENRICHER_PERSISTENCE_PROPERTY);
        
        mementoDir = Files.createTempDir();
        origManagementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader, 1);
        origApp = createApp();
    }
    
    /** optionally, create the app as part of every test; can be no-op if tests wish to set origApp themselves */
    protected abstract T createApp();

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        try {
            if (origApp != null) Entities.destroyAll(origApp.getManagementContext());
            if (newApp != null) Entities.destroyAll(newApp.getManagementContext());
            origApp = null;
            newApp = null;
            newManagementContext = null;
    
            if (origManagementContext != null) Entities.destroyAll(origManagementContext);
            if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
            origManagementContext = null;
        } finally {
            BrooklynFeatureEnablement.setEnablement(BrooklynFeatureEnablement.FEATURE_POLICY_PERSISTENCE_PROPERTY, origPolicyPersistenceEnabled);
            BrooklynFeatureEnablement.setEnablement(BrooklynFeatureEnablement.FEATURE_ENRICHER_PERSISTENCE_PROPERTY, origEnricherPersistenceEnabled);
        }
    }

    /** rebinds, and sets newApp */
    protected T rebind() throws Exception {
        if (newApp!=null || newManagementContext!=null) throw new IllegalStateException("already rebinded");
        newApp = rebind(true);
        newManagementContext = newApp.getManagementContext();
        return newApp;
    }

    protected T rebind(boolean checkSerializable) throws Exception {
        // TODO What are sensible defaults?!
        return rebind(checkSerializable, false);
    }
    
    @SuppressWarnings("unchecked")
    protected T rebind(boolean checkSerializable, boolean terminateOrigManagementContext) throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        if (checkSerializable) {
            RebindTestUtils.checkCurrentMementoSerializable(origApp);
        }
        if (terminateOrigManagementContext) {
            origManagementContext.terminate();
        }
        return (T) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }

    @SuppressWarnings("unchecked")
    protected T rebind(RebindExceptionHandler exceptionHandler) throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (T) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader(), exceptionHandler);
    }

    @SuppressWarnings("unchecked")
    protected T rebind(ManagementContext newManagementContext, RebindExceptionHandler exceptionHandler) throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (T) RebindTestUtils.rebind(newManagementContext, mementoDir, getClass().getClassLoader(), exceptionHandler);
    }
    
    protected BrooklynMementoManifest loadMementoManifest() throws Exception {
        BrooklynMementoPersisterToMultiFile persister = new BrooklynMementoPersisterToMultiFile(mementoDir, classLoader);
        RebindExceptionHandler exceptionHandler = new RecordingRebindExceptionHandler(RebindManager.RebindFailureMode.FAIL_AT_END, RebindManager.RebindFailureMode.FAIL_AT_END);
        BrooklynMementoManifest mementoManifest = persister.loadMementoManifest(exceptionHandler);
        persister.stop();
        return mementoManifest;
    }
}
