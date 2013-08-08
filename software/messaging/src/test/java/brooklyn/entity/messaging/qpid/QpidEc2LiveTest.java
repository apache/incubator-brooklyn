package brooklyn.entity.messaging.qpid;

import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;

public class QpidEc2LiveTest extends AbstractEc2LiveTest {

    // TODO Also check can connect (e.g. to send/receive messages)
    
    @Override
    protected void doTest(Location loc) throws Exception {
        QpidBroker qpid = app.createAndManageChild(EntitySpec.create(QpidBroker.class)
                .configure("jmxPort", "9909+")
                .configure("rmiServerPort", "9910+"));
        
        qpid.start(ImmutableList.of(loc));
        EntityTestUtils.assertAttributeEqualsEventually(qpid, QpidBroker.SERVICE_UP, true);
    }
    
    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  
}
