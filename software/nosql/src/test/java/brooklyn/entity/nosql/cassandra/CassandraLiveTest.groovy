package brooklyn.entity.nosql.cassandra

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

import brooklyn.config.BrooklynProperties
import brooklyn.location.LocationRegistry
import brooklyn.location.basic.BasicLocationRegistry
import brooklyn.location.basic.jclouds.JcloudsLocation
import brooklyn.util.text.Strings

/**
 * Cassandra live tests.
 *
 * Test the operation of the {@link CassandraServer} class using the jclouds {@code rackspace-cloudservers-uk}
 * and {@code aws-ec2} providers, with different OS images. The tests use the {@link #testConnection()} method
 * from the integration tests, and will need to have {@code brooklyn.jclouds.provider.identity} and {@code .credential}
 * set, usually in the {@code .brooklyn/bropoklyn.properties} file.
 */
public class CassandraLiveTest extends CassandraIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(CassandraLiveTest.class)

    @DataProvider(name = "virtualMachineData")
    public Object[][] provideVirtualMachineData() {
        return [ // ImageName, Provider, Region
            [ "ubuntu", "aws-ec2", "eu-west-1" ],
            [ "Ubuntu 12.0", "rackspace-cloudservers-uk", "" ],
            [ "CentOS 6.2", "rackspace-cloudservers-uk", "" ],
        ];
    }

    @Test(groups = "Live", dataProvider = "virtualMachineData")
    protected void testOperatingSystemProvider(String imageName, String provider, String region) throws Exception {
        log.info("Testing Cassandra on {}{} using {}", provider, Strings.isNonEmpty(region) ? ":" + region : "", imageName)

        BrooklynProperties props = BrooklynProperties.Factory.newDefault()
        props.remove(String.format("brooklyn.jclouds.%s.image-id", provider))
        props.put(String.format("brooklyn.jclouds.%s.image-name-matches", provider), imageName)
        props.put(String.format("brooklyn.jclouds.%s.inbound-ports", provider), "22,11099,9001,9876")
        LocationRegistry locationRegistry = new BasicLocationRegistry(props)

        testLocation = (JcloudsLocation) locationRegistry.resolve(provider + (Strings.isNonEmpty(region) ? ":" + region : ""))

        testConnection()
    }
}
