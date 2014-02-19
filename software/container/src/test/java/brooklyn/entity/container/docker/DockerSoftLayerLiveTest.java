package brooklyn.entity.container.docker;

import brooklyn.entity.AbstractSoftlayerLiveTest;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

@Test(groups="Live")
public class DockerSoftLayerLiveTest extends AbstractSoftlayerLiveTest {

    @Override
    protected void doTest(Location loc) throws Exception {
        DockerNode docker = app.createAndManageChild(EntitySpec.create(DockerNode.class)
                .configure("docker.port", "4244+"));
        app.start(ImmutableList.of(loc));
    }

    @Test(enabled=false)
    public void testDummy() { } // Convince testng IDE integration that this really does have test methods

    @Override
    @Test(enabled=false)
    public void test_Default() throws Exception {
    }

    @Override
    @Test(enabled=false)
    public void test_Centos_6_0() throws Exception {
    }

}
