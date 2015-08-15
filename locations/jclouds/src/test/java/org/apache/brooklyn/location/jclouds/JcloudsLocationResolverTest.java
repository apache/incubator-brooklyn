/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.location.jclouds;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.brooklyn.core.management.internal.LocalManagementContext;
import org.apache.brooklyn.location.basic.LocationInternal;
import org.apache.brooklyn.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.test.entity.LocalManagementContextForTests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.event.basic.SetConfigKey;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;

public class JcloudsLocationResolverTest {

    private static final Logger log = LoggerFactory.getLogger(JcloudsLocationResolverTest.class);
    
    private LocalManagementContext managementContext;
    private BrooklynProperties brooklynProperties;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        managementContext = LocalManagementContextForTests.newInstance();
        brooklynProperties = managementContext.getBrooklynProperties();

        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2.identity", "aws-ec2-id");
        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2.credential", "aws-ec2-cred");
        brooklynProperties.put("brooklyn.location.jclouds.rackspace-cloudservers-uk.identity", "cloudservers-uk-id");
        brooklynProperties.put("brooklyn.location.jclouds.rackspace-cloudservers-uk.credential", "cloudservers-uk-cred");
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (managementContext != null)
            managementContext.terminate();
    }

    @Test
    public void testJcloudsTakesDotSeparateProperty() {
        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2.loginUser.privateKeyFile", "myfile");
        String file = resolve("jclouds:aws-ec2").getConfig(JcloudsLocation.LOGIN_USER_PRIVATE_KEY_FILE);
        assertEquals(file, "myfile");
    }

    @Test
    public void testJcloudsTakesProviderScopedProperties() {
        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2.privateKeyFile", "myprivatekeyfile");
        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2.publicKeyFile", "mypublickeyfile");
        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2.privateKeyData", "myprivateKeyData");
        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2.publicKeyData", "myPublicKeyData");
        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2.privateKeyPassphrase", "myprivateKeyPassphrase");
        Map<String, Object> conf = resolve("jclouds:aws-ec2").config().getBag().getAllConfig();

        assertEquals(conf.get("privateKeyFile"), "myprivatekeyfile");
        assertEquals(conf.get("publicKeyFile"), "mypublickeyfile");
        assertEquals(conf.get("privateKeyData"), "myprivateKeyData");
        assertEquals(conf.get("publicKeyData"), "myPublicKeyData");
        assertEquals(conf.get("privateKeyPassphrase"), "myprivateKeyPassphrase");
    }

    @Test
    public void testJcloudsTakesGenericScopedProperties() {
        brooklynProperties.put("brooklyn.location.jclouds.privateKeyFile", "myprivatekeyfile");
        brooklynProperties.put("brooklyn.location.jclouds.publicKeyFile", "mypublickeyfile");
        brooklynProperties.put("brooklyn.location.jclouds.privateKeyData", "myprivateKeyData");
        brooklynProperties.put("brooklyn.location.jclouds.publicKeyData", "myPublicKeyData");
        brooklynProperties.put("brooklyn.location.jclouds.privateKeyPassphrase", "myprivateKeyPassphrase");
        Map<String, Object> conf = resolve("jclouds:aws-ec2").config().getBag().getAllConfig();

        assertEquals(conf.get("privateKeyFile"), "myprivatekeyfile");
        assertEquals(conf.get("publicKeyFile"), "mypublickeyfile");
        assertEquals(conf.get("privateKeyData"), "myprivateKeyData");
        assertEquals(conf.get("publicKeyData"), "myPublicKeyData");
        assertEquals(conf.get("privateKeyPassphrase"), "myprivateKeyPassphrase");
    }

    @Test
    public void testJcloudsTakesDeprecatedProperties() {
        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2.private-key-file", "myprivatekeyfile");
        brooklynProperties.put("brooklyn.location.jclouds.public-key-file", "mypublickeyfile");
        brooklynProperties.put("brooklyn.location.jclouds.private-key-data", "myprivateKeyData");
        brooklynProperties.put("brooklyn.location.jclouds.public-key-data", "myPublicKeyData");
        brooklynProperties.put("brooklyn.location.jclouds.private-key-passphrase", "myprivateKeyPassphrase");
        brooklynProperties.put("brooklyn.location.jclouds.image-id", "myimageid");
        Map<String, Object> conf = resolve("jclouds:aws-ec2").config().getBag().getAllConfig();

        assertEquals(conf.get("privateKeyFile"), "myprivatekeyfile");
        assertEquals(conf.get("publicKeyFile"), "mypublickeyfile");
        assertEquals(conf.get("privateKeyData"), "myprivateKeyData");
        assertEquals(conf.get("publicKeyData"), "myPublicKeyData");
        assertEquals(conf.get("privateKeyPassphrase"), "myprivateKeyPassphrase");
        assertEquals(conf.get("imageId"), "myimageid");
    }

    @Test
    public void testJcloudsPropertiesPrecedence() {
        brooklynProperties.put("brooklyn.location.named.myaws-ec2", "jclouds:aws-ec2");

        // prefer those in "named" over everything else
        brooklynProperties.put("brooklyn.location.named.myaws-ec2.privateKeyFile", "privateKeyFile-inNamed");
        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2.privateKeyFile", "privateKeyFile-inProviderSpecific");
        brooklynProperties.put("brooklyn.location.jclouds.privateKeyFile", "privateKeyFile-inJcloudsGeneric");

        // prefer those in provider-specific over generic
        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2.publicKeyFile", "publicKeyFile-inProviderSpecific");
        brooklynProperties.put("brooklyn.location.jclouds.publicKeyFile", "publicKeyFile-inJcloudsGeneric");

        // prefer deprecated properties in "named" over those less specific
        brooklynProperties.put("brooklyn.location.named.myaws-ec2.private-key-data", "privateKeyData-inNamed");
        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2.privateKeyData", "privateKeyData-inProviderSpecific");
        brooklynProperties.put("brooklyn.location.jclouds.privateKeyData", "privateKeyData-inJcloudsGeneric");

        // prefer generic if nothing else
        brooklynProperties.put("brooklyn.location.jclouds.publicKeyData", "publicKeyData-inJcloudsGeneric");

        // prefer "named" over everything else: confirm deprecated don't get
        // transformed to overwrite it accidentally
        brooklynProperties
                .put("brooklyn.location.named.myaws-ec2.privateKeyPassphrase", "privateKeyPassphrase-inNamed");
        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2.private-key-passphrase",
                "privateKeyPassphrase-inProviderSpecific");
        brooklynProperties.put("brooklyn.location.jclouds.private-key-passphrase", "privateKeyPassphrase-inJcloudsGeneric");

        Map<String, Object> conf = resolve("named:myaws-ec2").config().getBag().getAllConfig();

        assertEquals(conf.get("privateKeyFile"), "privateKeyFile-inNamed");
        assertEquals(conf.get("publicKeyFile"), "publicKeyFile-inProviderSpecific");
        assertEquals(conf.get("privateKeyData"), "privateKeyData-inNamed");
        assertEquals(conf.get("publicKeyData"), "publicKeyData-inJcloudsGeneric");
        assertEquals(conf.get("privateKeyPassphrase"), "privateKeyPassphrase-inNamed");
    }

    @Test
    public void testJcloudsLoads() {
        Assert.assertTrue(resolve("jclouds:aws-ec2") instanceof JcloudsLocation);
    }

    @Test
    public void testJcloudsImplicitLoads() {
        Assert.assertTrue(resolve("aws-ec2") instanceof JcloudsLocation);
    }

    @Test
    public void testJcloudsLocationLoads() {
        Assert.assertTrue(resolve("aws-ec2:eu-west-1") instanceof JcloudsLocation);
    }

    @Test
    public void testJcloudsRegionOnlyLoads() {
        Assert.assertTrue(resolve("eu-west-1") instanceof JcloudsLocation);
    }

    @Test
    public void testJcloudsEndpointLoads() {
        JcloudsLocation loc = resolve("jclouds:openstack-nova:http://foo/api");
        assertEquals(loc.getProvider(), "openstack-nova");
        assertEquals(loc.getEndpoint(), "http://foo/api");
    }

    @Test
    public void testJcloudsEndpointLoadsAsProperty() {
        brooklynProperties.put("brooklyn.location.jclouds.openstack-nova.endpoint", "myendpoint");
        JcloudsLocation loc = resolve("jclouds:openstack-nova");
        // just checking
        Assert.assertEquals(loc.config().getLocalBag().getStringKey("endpoint"), "myendpoint");
        Assert.assertEquals(loc.getConfig(CloudLocationConfig.CLOUD_ENDPOINT), "myendpoint");
        // this is the one we really care about!:
        assertEquals(loc.getEndpoint(), "myendpoint");
    }

    @Test
    public void testJcloudsLegacyRandomProperty() {
        brooklynProperties.put("brooklyn.location.jclouds.openstack-nova.foo", "bar");
        JcloudsLocation loc = resolve("jclouds:openstack-nova");
        Assert.assertEquals(loc.config().getLocalBag().getStringKey("foo"), "bar");
    }

    @Test
    public void testJcloudsRandomProperty() {
        brooklynProperties.put("brooklyn.location.jclouds.openstack-nova.foo", "bar");
        JcloudsLocation loc = resolve("jclouds:openstack-nova");
        Assert.assertEquals(loc.config().getLocalBag().getStringKey("foo"), "bar");
    }

    @Test
    public void testThrowsOnInvalid() throws Exception {
        // Tries to treat "wrongprefix" as a cloud provider
        assertThrows("wrongprefix:aws-ec2:us-east-1", NoSuchElementException.class);

        // no provider
        assertThrows("jclouds", IllegalArgumentException.class);

        // empty provider
        assertThrows("jclouds:", IllegalArgumentException.class);

        // invalid provider
        assertThrows("jclouds:doesnotexist", NoSuchElementException.class);
    }

    @Test
    public void testResolvesJclouds() throws Exception {
        // test with provider + region
        assertJcloudsEquals(resolve("jclouds:aws-ec2:us-east-1"), "aws-ec2", "us-east-1");

        // test with provider that has no region
        assertJcloudsEquals(resolve("jclouds:rackspace-cloudservers-uk"), "rackspace-cloudservers-uk", null);
    }

    @Test
    public void testJcloudsRegionOverridesParent() {
        Map<String, Object> conf;
        
        brooklynProperties.put("brooklyn.location.named.softlayer-was", "jclouds:softlayer:was01");
        brooklynProperties.put("brooklyn.location.named.softlayer-was2", "jclouds:softlayer:was01");
        brooklynProperties.put("brooklyn.location.named.softlayer-was2.region", "was02");
        conf = resolve("named:softlayer-was").config().getBag().getAllConfig();
        assertEquals(conf.get("region"), "was01");
        
        conf = resolve("named:softlayer-was2").config().getBag().getAllConfig();
        assertEquals(conf.get("region"), "was02");
        
        conf = ((LocationInternal) managementContext.getLocationRegistry().resolve("named:softlayer-was2", MutableMap.of("region", "was03")))
            .config().getBag().getAllConfig();;
        assertEquals(conf.get("region"), "was03");
    }
    
    // TODO Visual inspection test that it logs warnings
    @Test
    public void testLogsWarnings() throws Exception {
        assertJcloudsEquals(resolve("jclouds:jclouds:aws-ec2:us-east-1"), "aws-ec2", "us-east-1");
        assertJcloudsEquals(resolve("us-east-1"), "aws-ec2", "us-east-1");

        // TODO Should we enforce a jclouds prefix? Currently we don't
        // assertJcloudsEquals(resolve("aws-ec2:us-east-1"), "aws-ec2",
        // "us-east-1");

    }

    @Test
    public void testResolvesJcloudsFromNamedOfNamedWithPropertiesOverriddenCorrectly() throws Exception {
        brooklynProperties.put("brooklyn.location.jclouds.softlayer.prop1", "1");
        brooklynProperties.put("brooklyn.location.jclouds.softlayer.prop2", "1");
        brooklynProperties.put("brooklyn.location.jclouds.softlayer.prop3", "1");
        brooklynProperties.put("brooklyn.location.named.foo", "jclouds:softlayer:138124");
        brooklynProperties.put("brooklyn.location.named.foo.prop2", "2");
        brooklynProperties.put("brooklyn.location.named.foo.prop3", "2");
        brooklynProperties.put("brooklyn.location.named.bar", "named:foo");
        brooklynProperties.put("brooklyn.location.named.bar.prop3", "3");
        
        JcloudsLocation l = resolve("named:bar");
        assertJcloudsEquals(l, "softlayer", "138124");
        Assert.assertEquals(l.config().getLocalBag().getStringKey("prop3"), "3");
        Assert.assertEquals(l.config().getLocalBag().getStringKey("prop2"), "2");
        Assert.assertEquals(l.config().getLocalBag().getStringKey("prop1"), "1");
    }

    @Test
    public void testResolvesListAndMapProperties() throws Exception {
        brooklynProperties.put("brooklyn.location.jclouds.softlayer.prop1", "[ a, b ]");
        brooklynProperties.put("brooklyn.location.jclouds.softlayer.prop2", "{ a: 1, b: 2 }");
        brooklynProperties.put("brooklyn.location.named.foo", "jclouds:softlayer:ams01");
        
        JcloudsLocation l = resolve("named:foo");
        assertJcloudsEquals(l, "softlayer", "ams01");
        assertEquals(l.config().get(new SetConfigKey<String>(String.class, "prop1")), MutableSet.of("a", "b"));
        assertEquals(l.config().get(new MapConfigKey<String>(String.class, "prop2")), MutableMap.of("a", 1, "b", 2));
    }
    
    @Test
    public void testResolvesListAndMapPropertiesWithoutMergeOnInheritance() throws Exception {
        // when we have a yaml way to specify config we may wish to have different semantics;
        // it could depend on the collection config key whether to merge on inheritance
        brooklynProperties.put("brooklyn.location.jclouds.softlayer.prop1", "[ a, b ]");
        brooklynProperties.put("brooklyn.location.jclouds.softlayer.prop2", "{ a: 1, b: 2 }");
        brooklynProperties.put("brooklyn.location.named.foo", "jclouds:softlayer:ams01");
        
        brooklynProperties.put("brooklyn.location.named.foo.prop1", "[ a: 1, c: 3 ]");
        brooklynProperties.put("brooklyn.location.named.foo.prop2", "{ b: 3, c: 3 }");
        brooklynProperties.put("brooklyn.location.named.bar", "named:foo");
        brooklynProperties.put("brooklyn.location.named.bar.prop2", "{ c: 4, d: 4 }");
        
        // these do NOT affect the maps
        brooklynProperties.put("brooklyn.location.named.foo.prop2.z", "9");
        brooklynProperties.put("brooklyn.location.named.foo.prop3.z", "9");
        
        JcloudsLocation l = resolve("named:bar");
        assertJcloudsEquals(l, "softlayer", "ams01");
        
        Set<? extends String> prop1 = l.config().get(new SetConfigKey<String>(String.class, "prop1"));
        log.info("prop1: "+prop1);
        assertEquals(prop1, MutableSet.of("a: 1", "c: 3"));
        
        Map<String, String> prop2 = l.config().get(new MapConfigKey<String>(String.class, "prop2"));
        log.info("prop2: "+prop2);
        assertEquals(prop2, MutableMap.of("c", 4, "d", 4));
        
        Map<String, String> prop3 = l.config().get(new MapConfigKey<String>(String.class, "prop3"));
        log.info("prop3: "+prop3);
        assertEquals(prop3, null);
    }

    private void assertJcloudsEquals(JcloudsLocation loc, String expectedProvider, String expectedRegion) {
        assertEquals(loc.getProvider(), expectedProvider);
        assertEquals(loc.getRegion(), expectedRegion);
    }

    private void assertThrows(String val, Class<?> expectedExceptionType) throws Exception {
        try {
            resolve(val);
            fail();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e))
                throw e; // otherwise success

        }
    }

    @Test(expectedExceptions = { NoSuchElementException.class, IllegalArgumentException.class }, expectedExceptionsMessageRegExp = ".*insufficient.*")
    public void testJcloudsOnlyFails() {
        resolve("jclouds");
    }

    private JcloudsLocation resolve(String spec) {
        return (JcloudsLocation) managementContext.getLocationRegistry().resolve(spec);
    }
}
