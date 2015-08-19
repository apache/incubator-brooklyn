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
package org.apache.brooklyn.entity.brooklynnode;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.effector.core.EffectorBody;
import org.apache.brooklyn.entity.core.AbstractEntity;
import org.apache.brooklyn.entity.core.Attributes;
import org.apache.brooklyn.entity.core.Entities;
import org.apache.brooklyn.entity.core.EntityDynamicType;
import org.apache.brooklyn.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.sensor.core.Sensors;
import org.apache.brooklyn.sensor.feed.http.HttpFeed;
import org.apache.brooklyn.sensor.feed.http.HttpPollConfig;
import org.apache.brooklyn.util.collections.Jsonya;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.http.HttpToolResponse;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.net.Urls;
import org.apache.http.HttpStatus;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import com.google.gson.Gson;

public class BrooklynEntityMirrorImpl extends AbstractEntity implements BrooklynEntityMirror {
    @SuppressWarnings("rawtypes")
    private class MirrorSummary implements Function<HttpToolResponse, Map> {
        @Override
        public Map apply(HttpToolResponse input) {
            Map<?, ?> entitySummary = new Gson().fromJson(input.getContentAsString(), Map.class);
            String catalogItemId = (String)entitySummary.get("catalogItemId");
            setAttribute(MIRROR_CATALOG_ITEM_ID, catalogItemId);
            return entitySummary;
        }
    }

    private HttpFeed mirror;
    

    //Passively mirror entity's state
    @Override
    protected void initEnrichers() {}

    @Override
    public void init() {
        super.init();
        connectSensorsAsync();

        //start spinning, could take some time before MIRRORED_ENTITY_URL is available for first time mirroring
        setAttribute(Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STARTING);
    }

    @Override
    public void rebind() {
        super.rebind();
        connectSensorsAsync();
    }

    protected void connectSensorsAsync() {
        Callable<Void> asyncTask = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                //blocks until available (could be a task)
                String mirroredEntityUrl = getConfig(MIRRORED_ENTITY_URL);
                Preconditions.checkNotNull(mirroredEntityUrl, "Required config: "+MIRRORED_ENTITY_URL);

                connectSensors(mirroredEntityUrl);
                return null;
            }
        };

        DynamicTasks.queueIfPossible(
                Tasks.<Void>builder()
                    .name("Start entity mirror feed")
                    .body(asyncTask)
                    .build())
            .orSubmitAsync(this);
    }

    protected void connectSensors(String mirroredEntityUrl) {
        Function<HttpToolResponse, Void> mirrorSensors = new Function<HttpToolResponse,Void>() {
            @SuppressWarnings("rawtypes")
            @Override
            public Void apply(HttpToolResponse input) {
                Map sensors = new Gson().fromJson(input.getContentAsString(), Map.class);
                for (Object kv: sensors.entrySet())
                    setAttribute(Sensors.newSensor(Object.class, ""+((Map.Entry)kv).getKey()), ((Map.Entry)kv).getValue());
                setAttribute(MIRROR_STATUS, "normal");
                return null;
            }
        };

        final BrooklynEntityMirrorImpl self = this;
        mirror = HttpFeed.builder().entity(this)
            .baseUri(mirroredEntityUrl)
            .credentialsIfNotNull(getConfig(BrooklynNode.MANAGEMENT_USER), getConfig(BrooklynNode.MANAGEMENT_PASSWORD))
            .period(getConfig(POLL_PERIOD))
            .poll(HttpPollConfig.forMultiple()
                .suburl("/sensors/current-state")
                .onSuccess(mirrorSensors)
                .onFailureOrException(new Function<Object, Void>() {
                    @Override
                    public Void apply(Object input) {
                        ServiceStateLogic.updateMapSensorEntry(self, Attributes.SERVICE_PROBLEMS, "mirror-feed", "error contacting service");
                        if (input instanceof HttpToolResponse) {
                            int responseCode = ((HttpToolResponse)input).getResponseCode();
                            if (responseCode == HttpStatus.SC_NOT_FOUND) {
                                //the remote entity no longer exists
                                Entities.unmanage(self);
                            }
                        }
                        return null;
                    }
                }))
            .poll(HttpPollConfig.forSensor(MIRROR_SUMMARY).onSuccess(new MirrorSummary())).build();

        populateEffectors();
    }

    private void populateEffectors() {
        HttpToolResponse result = http().get("/effectors");
        Collection<?> cfgEffectors = new Gson().fromJson(result.getContentAsString(), Collection.class);
        Collection<Effector<String>> remoteEntityEffectors = RemoteEffectorBuilder.of(cfgEffectors);
        EntityDynamicType mutableEntityType = getMutableEntityType();
        for (Effector<String> eff : remoteEntityEffectors) {
            //remote already started
            if ("start".equals(eff.getName())) continue;
            mutableEntityType.addEffector(eff);
        }
    }

    protected void disconnectSensors() {
        if (mirror != null) mirror.stop();
    }

    @Override
    public void destroy() {
        disconnectSensors();
    }

    @Override
    public EntityHttpClient http() {
        return new EntityHttpClientImpl(this, MIRRORED_ENTITY_URL);
    }

    public static class RemoteEffector<T> extends EffectorBody<T> {
        public final String remoteEffectorName;
        public final Function<HttpToolResponse, T> resultParser;
        
        /** creates an effector implementation which POSTs to a remote effector endpoint, optionally converting
         * the byte[] response (if resultParser is null then null is returned) */
        public RemoteEffector(String remoteEffectorName, @Nullable Function<HttpToolResponse,T> resultParser) {
            this.remoteEffectorName = remoteEffectorName;
            this.resultParser = resultParser;
        }

        @Override
        public T call(ConfigBag parameters) {
            MutableMap<String, String> headers = MutableMap.of(com.google.common.net.HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
            byte[] httpBody = Jsonya.of(parameters.getAllConfig()).toString().getBytes();
            String effectorUrl = Urls.mergePaths("effectors", Urls.encode(remoteEffectorName));
            HttpToolResponse result = ((BrooklynEntityMirror)entity()).http().post(effectorUrl, headers, httpBody);
            if (resultParser!=null) return resultParser.apply(result);
            else return null;
        }
    }
}
