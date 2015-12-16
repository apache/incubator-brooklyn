/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.brooklyn.location.jclouds;

import java.util.Collections;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.exceptions.CompoundRuntimeException;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.Template;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

public class BailOutJcloudsLocation extends JcloudsLocation {

    public static final String ERROR_MESSAGE = "early termination for test";
    public static final RuntimeException BAIL_OUT_FOR_TESTING = new RuntimeException(ERROR_MESSAGE);
    
    // Don't care which image; not actually provisioning
    private static final String US_EAST_IMAGE_ID = "us-east-1/ami-7d7bfc14";

    public static final ConfigKey<Function<ConfigBag, Void>> BUILD_TEMPLATE_INTERCEPTOR = ConfigKeys.newConfigKey(
            new TypeToken<Function<ConfigBag, Void>>() {},
            "buildtemplateinterceptor");

    public static final ConfigKey<Boolean> BUILD_TEMPLATE = ConfigKeys.newBooleanConfigKey(
            "buildtemplate");

    ConfigBag lastConfigBag;
    Template template;

    public BailOutJcloudsLocation() {
        super();
    }

    public BailOutJcloudsLocation(Map<?, ?> conf) {
        super(conf);
    }

    @Override
    public Template buildTemplate(ComputeService computeService, ConfigBag config) {
        lastConfigBag = config;
        if (getConfig(BUILD_TEMPLATE_INTERCEPTOR) != null) {
            getConfig(BUILD_TEMPLATE_INTERCEPTOR).apply(config);
        }
        if (Boolean.TRUE.equals(getConfig(BUILD_TEMPLATE))) {
            template = super.buildTemplate(computeService, config);
        }
        throw new RuntimeException(BAIL_OUT_FOR_TESTING);
    }

    public Template getTemplate() {
        return template;
    }

    public void tryObtain() {
        tryObtain(Collections.emptyMap());
    }

    public void tryObtain(Map<?, ?> flags) {
        tryObtainAndCheck(flags, Predicates.alwaysTrue());
    }

    public void tryObtainAndCheck(Map<?, ?> flags, Predicate<? super ConfigBag> test) {
        try {
            obtain(flags);
        } catch (Exception e) {
            boolean found = Iterables.tryFind(Throwables.getCausalChain(e), Predicates.<Throwable>equalTo(e)).isPresent();
            if (!found && e instanceof CompoundRuntimeException) {
                for (Throwable cause : ((CompoundRuntimeException) e).getAllCauses()) {
                    found = Iterables.tryFind(Throwables.getCausalChain(cause), Predicates.<Throwable>equalTo(e)).isPresent();
                    if (found) break;
                }
            }
            if (found) {
                test.apply(lastConfigBag);
            } else {
                throw Exceptions.propagate(e);
            }
        }
    }

    @Override
    @VisibleForTesting
    public UserCreation createUserStatements(@Nullable Image image, ConfigBag config) {
        return super.createUserStatements(image, config);
    }


    public static BailOutJcloudsLocation newBailOutJcloudsLocation(ManagementContext mgmt) {
        return newBailOutJcloudsLocation(mgmt, Collections.<ConfigKey<?>, Object>emptyMap());
    }

    public static BailOutJcloudsLocation newBailOutJcloudsLocation(ManagementContext mgmt, Map<ConfigKey<?>, ?> config) {
        Map<ConfigKey<?>, ?> allConfig = MutableMap.<ConfigKey<?>, Object>builder()
                .put(IMAGE_ID, "bogus")
                .put(CLOUD_PROVIDER, "aws-ec2")
                .put(ACCESS_IDENTITY, "bogus")
                .put(CLOUD_REGION_ID, "bogus")
                .put(ACCESS_CREDENTIAL, "bogus")
                .put(USER, "fred")
                .put(MIN_RAM, 16)
                .put(JcloudsLocation.MACHINE_CREATE_ATTEMPTS, 1)
                .putAll(config)
                .build();
        return mgmt.getLocationManager().createLocation(
                LocationSpec.create(BailOutJcloudsLocation.class).configure(allConfig));
    }


    // todo better name

    /** As {@link BailOutJcloudsLocation}, counting the number of {@link #buildTemplate} calls. */
    public static class CountingBailOutJcloudsLocation extends BailOutJcloudsLocation {
        int buildTemplateCount = 0;
        @Override
        public Template buildTemplate(ComputeService computeService, ConfigBag config) {
            buildTemplateCount++;
            return super.buildTemplate(computeService, config);
        }
    }

    public static CountingBailOutJcloudsLocation newCountingBailOutJcloudsLocation(ManagementContext mgmt, Map flags) {
        LocationSpec<CountingBailOutJcloudsLocation> spec = LocationSpec.create(CountingBailOutJcloudsLocation.class)
                .configure(flags);
        return mgmt.getLocationManager().createLocation(spec);
    }

    /** @see #newBailOutJcloudsLocationForLiveTest(LocalManagementContext, Map)} */
    public static BailOutJcloudsLocation newBailOutJcloudsLocationForLiveTest(LocalManagementContext mgmt) {
        return newBailOutJcloudsLocationForLiveTest(mgmt, Collections.<ConfigKey<?>, Object>emptyMap());
    }

    /**
     * Takes identity and access credential from management context's Brooklyn properties and sets
     * inbound ports to [22, 80, 9999].
     */
    public static BailOutJcloudsLocation newBailOutJcloudsLocationForLiveTest(LocalManagementContext mgmt, Map<ConfigKey<?>, ?> config) {
        BrooklynProperties brooklynProperties = mgmt.getBrooklynProperties();
        String identity = (String) brooklynProperties.get("brooklyn.location.jclouds.aws-ec2.identity");
        if (identity == null) identity = (String) brooklynProperties.get("brooklyn.jclouds.aws-ec2.identity");
        String credential = (String) brooklynProperties.get("brooklyn.location.jclouds.aws-ec2.credential");
        if (credential == null) credential = (String) brooklynProperties.get("brooklyn.jclouds.aws-ec2.credential");

        Map<ConfigKey<?>, ?> allConfig = MutableMap.<ConfigKey<?>, Object>builder()
                .put(CLOUD_PROVIDER, AbstractJcloudsLiveTest.AWS_EC2_PROVIDER)
                .put(CLOUD_REGION_ID, AbstractJcloudsLiveTest.AWS_EC2_USEAST_REGION_NAME)
                .put(IMAGE_ID, US_EAST_IMAGE_ID) // so Brooklyn does not attempt to load all EC2 images
                .put(ACCESS_IDENTITY, identity)
                .put(ACCESS_CREDENTIAL, credential)
                .put(INBOUND_PORTS, "[22, 80, 9999]")
                .put(BUILD_TEMPLATE, true)
                .putAll(config)
                .build();

        return newBailOutJcloudsLocation(mgmt, allConfig);
    }

}
