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
package org.apache.brooklyn.camp.brooklyn;

import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynYamlTypeInstantiator;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynYamlTypeInstantiator.Factory;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynYamlTypeInstantiator.InstantiatorFromKey;
import org.apache.brooklyn.core.mgmt.classloading.JavaBrooklynClassLoadingContext;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.apache.brooklyn.policy.ha.ServiceRestarter;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.javalang.JavaClassNames;
import org.apache.brooklyn.util.time.Duration;

public class BrooklynYamlTypeInstantiatorTest extends AbstractYamlTest {

    protected BrooklynClassLoadingContext loader() {
        return JavaBrooklynClassLoadingContext.create(mgmt());
    }
    
    @Test
    public void testLoadPolicySpecProgrammatically() {
        Factory loader = new BrooklynYamlTypeInstantiator.Factory(loader(), "test:"+JavaClassNames.niceClassAndMethod());
        InstantiatorFromKey decoL = loader.from(MutableMap.of("some_type", ServiceRestarter.class.getName())).prefix("some");
        
        Assert.assertTrue(decoL.getConfigMap().isEmpty());
        Assert.assertEquals(decoL.getTypeName().get(), ServiceRestarter.class.getName());
        Assert.assertEquals(decoL.getType(), ServiceRestarter.class);
        
        Object sl1 = decoL.newInstance();
        Assert.assertTrue(sl1 instanceof ServiceRestarter);
        
        Policy sl2 = decoL.newInstance(Policy.class);
        Assert.assertTrue(sl2 instanceof ServiceRestarter);
    }
    
    @Test
    public void testLoadPolicySpecWithBrooklynConfig() {
        Factory loader = new BrooklynYamlTypeInstantiator.Factory(loader(), "test:"+JavaClassNames.niceClassAndMethod());
        InstantiatorFromKey decoL = loader.from(MutableMap.of("some_type", ServiceRestarter.class.getName(),
            "brooklyn.config", MutableMap.of("failOnRecurringFailuresInThisDuration", Duration.seconds(42)))).prefix("some");
        Policy sl2 = decoL.newInstance(Policy.class);
        Assert.assertEquals(sl2.getConfig(ServiceRestarter.FAIL_ON_RECURRING_FAILURES_IN_THIS_DURATION).toSeconds(), 42);
    }

    @Test(groups = "WIP")
    public void testLoadPolicySpecWithFlag() {
        Factory loader = new BrooklynYamlTypeInstantiator.Factory(loader(), "test:"+JavaClassNames.niceClassAndMethod());
        InstantiatorFromKey decoL = loader.from(MutableMap.of("some_type", ServiceRestarter.class.getName(),
            "failOnRecurringFailuresInThisDuration", Duration.seconds(42))).prefix("some");
        Policy sl2 = decoL.newInstance(Policy.class);
        Assert.assertEquals(sl2.getConfig(ServiceRestarter.FAIL_ON_RECURRING_FAILURES_IN_THIS_DURATION).toSeconds(), 42);
    }

}
