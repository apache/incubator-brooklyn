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
package brooklyn.rest.transform;

import static brooklyn.rest.domain.Status.ACCEPTED;
import static brooklyn.rest.domain.Status.RUNNING;
import static brooklyn.rest.domain.Status.STARTING;
import static brooklyn.rest.domain.Status.STOPPED;
import static brooklyn.rest.domain.Status.STOPPING;
import static brooklyn.rest.domain.Status.UNKNOWN;
import static brooklyn.rest.domain.Status.DESTROYED;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import javax.annotation.Nullable;

import brooklyn.entity.Application;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.ApplicationSummary;
import brooklyn.rest.domain.Status;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;

public class ApplicationTransformer {

    public static final Function<? super Application, ApplicationSummary> FROM_APPLICATION = new Function<Application, ApplicationSummary>() {
        @Override
        public ApplicationSummary apply(Application input) {
            return summaryFromApplication(input);
        }
    };

    public static Status statusFromApplication(Application application) {
        if (application == null) return UNKNOWN;
        Lifecycle state = application.getAttribute(Attributes.SERVICE_STATE_ACTUAL);
        if (state != null) return statusFromLifecycle(state);
        Boolean up = application.getAttribute(Startable.SERVICE_UP);
        if (up != null && up.booleanValue()) return RUNNING;
        return UNKNOWN;
    }


    public static Status statusFromLifecycle(Lifecycle state) {
        if (state == null) return UNKNOWN;
        switch (state) {
            case CREATED:
                return ACCEPTED;
            case STARTING:
                return STARTING;
            case RUNNING:
                return RUNNING;
            case STOPPING:
                return STOPPING;
            case STOPPED:
                return STOPPED;
            case DESTROYED:
                return DESTROYED;
            case ON_FIRE:
            default:
                return UNKNOWN;
        }
    }

    public static ApplicationSpec specFromApplication(Application application) {
        Collection<String> locations = Collections2.transform(application.getLocations(), new Function<Location, String>() {
            @Override
            @Nullable
            public String apply(@Nullable Location input) {
                return input.getId();
            }
        });
        // okay to have entities and config as null, as this comes from a real instance
        return new ApplicationSpec(application.getDisplayName(), application.getEntityType().getName(),
                null, locations, null);
    }

    public static ApplicationSummary summaryFromApplication(Application application) {
        Map<String, URI> links;
        if (application.getId() == null) {
            links = Collections.emptyMap();
        } else {
            links = ImmutableMap.of(
                    "self", URI.create("/v1/applications/" + application.getId()),
                    "entities", URI.create("/v1/applications/" + application.getId() + "/entities"));
        }

        return new ApplicationSummary(application.getId(), specFromApplication(application), statusFromApplication(application), links);
    }
}
