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
package org.apache.brooklyn.core.location;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.location.AbstractLocation;
import org.apache.brooklyn.core.location.internal.LocationInternal;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

public class LocationConfigTest {

    // TODO Duplication of LocationConfigTest, but with locations instead of entities
    
    private ManagementContext managementContext;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = LocalManagementContextForTests.newInstance();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }

    @Test
    public void testConfigBagContainsMatchesForConfigKeyName() throws Exception {
        LocationInternal loc = managementContext.getLocationManager().createLocation(LocationSpec.create(MyLocation.class)
                .configure("mylocation.myconfig", "myval1")
                .configure("mylocation.myconfigwithflagname", "myval2"));
        
        assertEquals(loc.config().getBag().getAllConfig(), ImmutableMap.of("mylocation.myconfig", "myval1", "mylocation.myconfigwithflagname", "myval2"));
        assertEquals(loc.config().getLocalBag().getAllConfig(), ImmutableMap.of("mylocation.myconfig", "myval1", "mylocation.myconfigwithflagname", "myval2"));
        Assert.assertEquals(loc.getAllConfig(true), ImmutableMap.of("mylocation.myconfig", "myval1", "mylocation.myconfigwithflagname", "myval2"));
        Assert.assertEquals(loc.getAllConfig(false), ImmutableMap.of("mylocation.myconfig", "myval1", "mylocation.myconfigwithflagname", "myval2"));
    }

    // TODO Note difference compared to Location, where both flag-name + config-key-name are in the ConfigBag
    @Test
    public void testConfigBagContainsMatchesForFlagName() throws Exception {
        // Prefers flag-name, over config-key's name
        LocationInternal loc = managementContext.getLocationManager().createLocation(LocationSpec.create(MyLocation.class)
                .configure("myconfigflagname", "myval"));
        
        assertEquals(loc.config().getBag().getAllConfig(), ImmutableMap.of("mylocation.myconfigwithflagname", "myval", "myconfigflagname", "myval"));
        assertEquals(loc.config().getLocalBag().getAllConfig(), ImmutableMap.of("mylocation.myconfigwithflagname", "myval", "myconfigflagname", "myval"));
        Assert.assertEquals(loc.getAllConfig(true), ImmutableMap.of("mylocation.myconfigwithflagname", "myval", "myconfigflagname", "myval"));
        Assert.assertEquals(loc.getAllConfig(false), ImmutableMap.of("mylocation.myconfigwithflagname", "myval", "myconfigflagname", "myval"));
    }

    @Test
    public void testConfigBagContainsUnmatched() throws Exception {
        LocationInternal loc = managementContext.getLocationManager().createLocation(LocationSpec.create(MyLocation.class)
                .configure("notThere", "notThereVal"));
        
        assertEquals(loc.config().getBag().getAllConfig(), ImmutableMap.of("notThere", "notThereVal"));
        assertEquals(loc.config().getLocalBag().getAllConfig(), ImmutableMap.of("notThere", "notThereVal"));
        Assert.assertEquals(loc.getAllConfig(true), ImmutableMap.of("notThere", "notThereVal"));
        Assert.assertEquals(loc.getAllConfig(false), ImmutableMap.of("notThere", "notThereVal"));
    }
    
    // TODO Note difference from entity: child's bag contains both the flag-name and the config-key-name
    @Test
    public void testChildConfigBagInheritsUnmatchedAtParent() throws Exception {
        LocationInternal loc = managementContext.getLocationManager().createLocation(LocationSpec.create(MyLocation.class)
                .configure("mychildlocation.myconfig", "myval1")
                .configure("notThere", "notThereVal"));

        LocationInternal child = managementContext.getLocationManager().createLocation(LocationSpec.create(MyChildLocation.class)
                .parent(loc));

        assertEquals(child.config().getBag().getAllConfig(), ImmutableMap.of("mychildlocation.myconfig", "myval1", "notThere", "notThereVal"));
        assertEquals(child.config().getLocalBag().getAllConfig(), ImmutableMap.of());
        Assert.assertEquals(child.getAllConfig(true), ImmutableMap.of("mychildlocation.myconfig", "myval1", "notThere", "notThereVal"));
        Assert.assertEquals(child.getAllConfig(false), ImmutableMap.of());
    }
    
    // TODO Fails for location, but passes for entity; not worth fixing here; locations will soon be entities!
    @Test(groups="WIP")
    public void testChildConfigBagInheritsFlagNameFromParentSetsOwnConfigKey() throws Exception {
        LocationInternal loc = managementContext.getLocationManager().createLocation(LocationSpec.create(MyLocation.class)
                .configure("mychildconfigflagname", "myval"));

        LocationInternal child = managementContext.getLocationManager().createLocation(LocationSpec.create(MyChildLocation.class)
                .parent(loc));

        assertEquals(child.config().getBag().getAllConfig(), ImmutableMap.of("mychildlocation.myconfigwithflagname", "myval"));
        assertEquals(child.config().getLocalBag().getAllConfig(), ImmutableMap.of());
        Assert.assertEquals(child.getAllConfig(true), ImmutableMap.of("mychildlocation.myconfigwithflagname", "myval"));
        Assert.assertEquals(child.getAllConfig(false), ImmutableMap.of());
    }
    
    @Test
    public void testChildInheritsFromParent() throws Exception {
        LocationInternal loc = managementContext.getLocationManager().createLocation(LocationSpec.create(MyLocation.class)
                .configure("mylocation.myconfig", "myval1"));

        LocationInternal child = managementContext.getLocationManager().createLocation(LocationSpec.create(MyChildLocation.class)
                .parent(loc));

        assertEquals(child.config().getBag().getAllConfig(), ImmutableMap.of("mylocation.myconfig", "myval1"));
        assertEquals(child.config().getLocalBag().getAllConfig(), ImmutableMap.of());
        Assert.assertEquals(child.getAllConfig(true), ImmutableMap.of("mylocation.myconfig", "myval1"));
        Assert.assertEquals(child.getAllConfig(false), ImmutableMap.of());
    }
    
    @Test
    public void testChildCanOverrideConfigUsingKeyName() throws Exception {
        LocationInternal location = managementContext.getLocationManager().createLocation(LocationSpec.create(MyLocation.class)
                .configure("mychildlocation.myconfigwithflagname", "myval")
                .configure("notThere", "notThereVal"));

        LocationInternal child = managementContext.getLocationManager().createLocation(LocationSpec.create(MyChildLocation.class)
                .parent(location)
                .configure("mychildlocation.myconfigwithflagname", "overrideMyval")
                .configure("notThere", "overrideNotThereVal"));

        assertEquals(child.config().getBag().getAllConfig(), ImmutableMap.of("mychildlocation.myconfigwithflagname", "overrideMyval", "notThere", "overrideNotThereVal"));
        assertEquals(child.config().getLocalBag().getAllConfig(), ImmutableMap.of("mychildlocation.myconfigwithflagname", "overrideMyval", "notThere", "overrideNotThereVal"));
        Assert.assertEquals(child.getAllConfig(true), ImmutableMap.of("mychildlocation.myconfigwithflagname", "overrideMyval", "notThere", "overrideNotThereVal"));
        Assert.assertEquals(child.getAllConfig(false), ImmutableMap.of("mychildlocation.myconfigwithflagname", "overrideMyval", "notThere", "overrideNotThereVal"));
    }
    
    // TODO Note difference compared to Location, where both flag-name + config-key-name are in the ConfigBag
    @Test
    public void testChildCanOverrideConfigUsingFlagName() throws Exception {
        LocationInternal loc = managementContext.getLocationManager().createLocation(LocationSpec.create(MyLocation.class)
                .configure("mychildlocation.myconfigwithflagname", "myval"));

        LocationInternal child = managementContext.getLocationManager().createLocation(LocationSpec.create(MyChildLocation.class)
                .parent(loc)
                .configure("mychildconfigflagname", "overrideMyval"));

        assertEquals(child.config().getBag().getAllConfig(), ImmutableMap.of("mychildlocation.myconfigwithflagname", "overrideMyval", "mychildconfigflagname", "overrideMyval"));
        assertEquals(child.config().getLocalBag().getAllConfig(), ImmutableMap.of("mychildlocation.myconfigwithflagname", "overrideMyval", "mychildconfigflagname", "overrideMyval"));
        Assert.assertEquals(child.getAllConfig(true), ImmutableMap.of("mychildlocation.myconfigwithflagname", "overrideMyval", "mychildconfigflagname", "overrideMyval"));
        Assert.assertEquals(child.getAllConfig(false), ImmutableMap.of("mychildlocation.myconfigwithflagname", "overrideMyval", "mychildconfigflagname", "overrideMyval"));
    }
    
    @Test
    public void testLocationCanOverrideConfigDefaultValue() throws Exception {
        LocationInternal loc = managementContext.getLocationManager().createLocation(LocationSpec.create(MyLocation.class));
        LocationInternal subloc = managementContext.getLocationManager().createLocation(LocationSpec.create(MySubLocation.class));

        Assert.assertEquals(loc.getConfig(MyLocation.MY_CONFIG_WITH_DEFAULT), "mydefault");
        Assert.assertEquals(loc.getConfig(ConfigKeys.newStringConfigKey("mylocation.myconfigwithdefault", "", "differentdefault")), "mydefault");
        
        Assert.assertEquals(subloc.getConfig(MySubLocation.MY_CONFIG_WITH_DEFAULT), "mysubdefault");
        Assert.assertEquals(subloc.getConfig(MyLocation.MY_CONFIG_WITH_DEFAULT), "mysubdefault");
    }
    
    @SuppressWarnings("serial")
    public static class MyLocation extends AbstractLocation {
        public static final ConfigKey<String> MY_CONFIG = ConfigKeys.newStringConfigKey("mylocation.myconfig");

        @SetFromFlag("myconfigflagname")
        public static final ConfigKey<String> MY_CONFIG_WITH_FLAGNAME = ConfigKeys.newStringConfigKey("mylocation.myconfigwithflagname");
        
        public static final ConfigKey<String> MY_CONFIG_WITH_DEFAULT = ConfigKeys.newStringConfigKey("mylocation.myconfigwithdefault", "", "mydefault");
    }
    
    @SuppressWarnings("serial")
    public static class MyChildLocation extends AbstractLocation {
        public static final ConfigKey<String> MY_CHILD_CONFIG = ConfigKeys.newStringConfigKey("mychildlocation.myconfig");

        @SetFromFlag("mychildconfigflagname")
        public static final ConfigKey<String> MY_CHILD_CONFIG_WITH_FLAGNAME = ConfigKeys.newStringConfigKey("mychildlocation.myconfigwithflagname");
    }
    
    @SuppressWarnings("serial")
    public static class MySubLocation extends MyLocation {
        public static final ConfigKey<String> MY_CONFIG_WITH_DEFAULT = ConfigKeys.newConfigKeyWithDefault(MyLocation.MY_CONFIG_WITH_DEFAULT, "mysubdefault");
    }
    
}
