package brooklyn.extras.whirr.hadoop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;

public class WhirrHadoopClusterLiveTest {

    private static final Logger log = LoggerFactory.getLogger(WhirrHadoopClusterLiveTest.class);
    
    public static final String PROVIDER = "aws-ec2";
    public static final String LOCATION_SPEC = "jclouds:"+PROVIDER;
    
    protected TestApplication app;
    protected Location jcloudsLocation;
    private BrooklynProperties brooklynProperties;
    private LocalManagementContext ctx;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        ctx = new LocalManagementContext(brooklynProperties);
        app = ApplicationBuilder.newManagedApp(TestApplication.class, ctx);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test(groups = { "Live" })
    public void testAwsRollout() {
        try {
            //final WhirrHadoopCluster hadoop = 
            app.createAndManageChild(EntitySpec.create(WhirrHadoopCluster.class));
            Location loc = ctx.getLocationRegistry().resolve(LOCATION_SPEC);
            app.start(ImmutableList.of(loc));
        } finally {
            log.info("completed AWS Hadoop test: "+app.getAllAttributes());
            Entities.dumpInfo(app);
        }
    }

}
