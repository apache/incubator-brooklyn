package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;

import java.io.File;
import java.io.FileReader;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.stream.Streams;

public class RebindEntityDynamicTypeInfoTest extends RebindTestFixtureWithApp {

    private static final Logger log = LoggerFactory.getLogger(RebindEntityDynamicTypeInfoTest.class);
    
    
    @Override
    protected TestApplication rebind() throws Exception {
        return rebind(false);
    }
    
    public static class SayHiBody extends EffectorBody<String> {
        public static final ConfigKey<String> NAME_KEY = ConfigKeys.newStringConfigKey("name");
        public static final Effector<String> EFFECTOR = Effectors.effector(String.class, "say_hi").description("says hello")
            .parameter(NAME_KEY).impl(new SayHiBody()).build();
        
        @Override
        public String call(ConfigBag parameters) {
            return "hello "+parameters.get(NAME_KEY);
        }
    }
    
    @Test(enabled=false, groups="WIP")
    // TODO re-enable, one we save entityType
    public void testRestoresEffectorStaticClass() throws Exception {
        origApp.getMutableEntityType().addEffector(SayHiBody.EFFECTOR);
        checkEffectorWithRebind();
    }
    
    @Test(enabled=false, groups="WIP")
    // TODO make sure entityType is concise
    public void testRestoresEffectorStaticClassCheckMemento() throws Exception {
        origApp.getMutableEntityType().addEffector(SayHiBody.EFFECTOR);
        RebindTestUtils.waitForPersisted(origApp);
        
        File mementoFile = new File(new File(mementoDir, "entities"), origApp.getId());
        String memento = Streams.readFully(new FileReader(mementoFile));
        log.info("memento is:\n"+memento);
        // make sure it's not too long
        Assert.assertTrue(memento.length() < 2000);
    }

    // does not work, as the class is anonymous not static so pulls in too much stuff from the test fixture
    // (including e.g. mgmt context and non-serializable guice bindings)
    @Test(enabled=false, groups="WIP")
    public void testRestoresEffectorAnonymousClass() throws Exception {
        origApp.getMutableEntityType().addEffector(Effectors.effector(String.class, "say_hi")
            .parameter(SayHiBody.NAME_KEY)
            .description("says hello")
            .impl(new EffectorBody<String>() {
                @Override
                public String call(ConfigBag parameters) {
                    return "hello "+parameters.get(SayHiBody.NAME_KEY);
                }
            }).build());
        checkEffectorWithRebind();
        
    }

    private void checkEffectorWithRebind() throws InterruptedException, ExecutionException,
        Exception {
        Effector<?> eff = origApp.getEntityType().getEffectorByName("say_hi").get();
        assertEquals(origApp.invoke(eff, ConfigBag.newInstance().configure(SayHiBody.NAME_KEY, "bob").getAllConfig()).get(), "hello bob");
        
        newApp = rebind();
        log.info("Effectors on new app: "+newApp.getEntityType().getEffectors());
        assertNotSame(newApp, origApp);
        assertEquals(newApp.getId(), origApp.getId());
        
        assertEquals(newApp.invoke(eff, ConfigBag.newInstance().configure(SayHiBody.NAME_KEY, "bob").getAllConfig()).get(), "hello bob");
        eff = newApp.getEntityType().getEffectorByName("say_hi").get();
        assertEquals(newApp.invoke(eff, ConfigBag.newInstance().configure(SayHiBody.NAME_KEY, "bob").getAllConfig()).get(), "hello bob");
    }
    
}
