/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.couchdb

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

import brooklyn.entity.proxying.BasicEntitySpec
import brooklyn.entity.trait.Startable
import brooklyn.location.jclouds.JcloudsLocation
import brooklyn.util.MutableMap
import brooklyn.util.text.Strings

import com.google.common.collect.ImmutableList

/**
 * CouchDB live tests.
 *
 * Test the operation of the {@link CouchDBNode} class using the jclouds {@code rackspace-cloudservers-uk}
 * and {@code aws-ec2} providers, with different OS images. The tests use the {@link #astyanaxTest()} method
 * to exercise the node, and will need to have {@code brooklyn.jclouds.provider.identity} and {@code .credential}
 * set, usually in the {@code .brooklyn/brooklyn.properties} file.
 */
public class CouchDBNodeLiveTest extends AbstractCouchDBNodeTest {
    private static final Logger log = LoggerFactory.getLogger(CouchDBNodeLiveTest.class)

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
        log.info("Testing CouchDB on {}{} using {}", provider, Strings.isNonEmpty(region) ? ":" + region : "", imageName)

        Map<String, String> properties = MutableMap.of("image-name-matches", imageName);
        testLocation = (JcloudsLocation) app.getManagementContext().getLocationRegistry()
                .resolve(provider + (Strings.isNonEmpty(region) ? ":" + region : ""), properties)

        couchdb = app.createAndManageChild(BasicEntitySpec.newInstance(CouchDBNode.class)
                .configure("httpPort", "12345+")
                .configure("clusterName", "TestCluster"));
        app.start(ImmutableList.of(testLocation))
        executeUntilSucceeds(timeout:2*TimeUnit.MINUTES) {
            assertTrue couchdb.getAttribute(Startable.SERVICE_UP)
        }

        jcouchdbTest(couchdb)
    }
}
