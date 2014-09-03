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
package brooklyn.entity.brooklynnode;

import java.net.URI;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFunctions;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.event.basic.Sensors;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.util.collections.Jsonya;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Functionals;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpTool.HttpClientBuilder;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.net.Urls;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.Tasks;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.net.MediaType;
import com.google.gson.Gson;

public class BrooklynEntityMirrorImpl extends AbstractEntity implements BrooklynEntityMirror {

    private static final Logger log = LoggerFactory.getLogger(BrooklynEntityMirrorImpl.class);
    
    private HttpFeed mirror;
    

    //Passively mirror entity's state
    @Override
    protected void initEnrichers() {}

    @Override
    public void init() {
        super.init();
        connectSensors();
    }

    protected void connectSensors() {
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
        
        String sensorsUri = Urls.mergePaths(
            Preconditions.checkNotNull(getConfig(MIRRORED_ENTITY_URL), "Required config: "+MIRRORED_ENTITY_URL),
            "sensors/current-state");
        
        mirror = HttpFeed.builder().entity(this)
            .baseUri(sensorsUri)
            .credentialsIfNotNull(getConfig(BrooklynNode.MANAGEMENT_USER), getConfig(BrooklynNode.MANAGEMENT_PASSWORD))
            .period(getConfig(POLL_PERIOD))
            .poll(HttpPollConfig.forMultiple()
                .onSuccess(mirrorSensors)
                .onFailureOrException(Functionals.function(EntityFunctions.updatingSensorMapEntry(this, Attributes.SERVICE_PROBLEMS, "mirror-feed",
                        Suppliers.ofInstance("error contacting service")
                    ))))
            .build();
    }

    protected void disconnectSensors() {
        if (mirror != null) mirror.stop();
    }

    @Override
    public void destroy() {
        disconnectSensors();
    }

    public static class RemoteEffector<T> extends EffectorBody<T> {
        public final String remoteEffectorName;
        public final Function<byte[], T> resultParser;
        
        /** creates an effector implementation which POSTs to a remote effector endpoint, optionally converting
         * the byte[] response (if resultParser is null then null is returned) */
        public RemoteEffector(String remoteEffectorName, @Nullable Function<byte[],T> resultParser) {
            this.remoteEffectorName = remoteEffectorName;
            this.resultParser = resultParser;
        }

        @Override
        public T call(ConfigBag parameters) {
            String baseUri = Preconditions.checkNotNull(entity().getConfig(MIRRORED_ENTITY_URL), "Cannot be invoked without an entity URL");
            HttpClientBuilder builder = HttpTool.httpClientBuilder()
                .trustAll()
                .laxRedirect(true)
                .uri(baseUri);
            if (entity().getConfig(MANAGEMENT_USER)!=null)
                builder.credentials(new UsernamePasswordCredentials(entity().getConfig(MANAGEMENT_USER), entity().getConfig(MANAGEMENT_PASSWORD)));
            HttpClient client = builder.build();
            
            byte[] result = submit(client, URI.create(Urls.mergePaths(baseUri, "effectors", Urls.encode(remoteEffectorName))), parameters.getAllConfig());
            if (resultParser!=null) return resultParser.apply(result);
            else return null;
        }

        @VisibleForTesting
        public static byte[] submit(HttpClient client, URI uri, Map<String,Object> args) {
            HttpToolResponse result = null;
            byte[] content;
            try {
                result = HttpTool.httpPost(client, uri, MutableMap.of(com.google.common.net.HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()), 
                    Jsonya.of(args).toString().getBytes());
                content = result.getContent();
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                throw new IllegalStateException("Invalid response invoking "+uri+": "+e, e);
            }
            Tasks.addTagDynamically(BrooklynTaskTags.tagForStream("http_response", Streams.byteArray(content)));
            if (!HttpTool.isStatusCodeHealthy(result.getResponseCode())) {
                log.warn("Invalid response invoking "+uri+": response code "+result.getResponseCode()+"\n"+result+": "+new String(content));
                throw new IllegalStateException("Invalid response invoking "+uri+": response code "+result.getResponseCode());
            }
            return content;
        }

    }

    public static class StopAndExpungeEffector extends RemoteEffector<Void> {
        public StopAndExpungeEffector() {
            super("stop", null);
        }
        @Override
        public Void call(ConfigBag parameters) {
            super.call(parameters);
            Entities.unmanage(entity());
            return null;
        }
    }
}
