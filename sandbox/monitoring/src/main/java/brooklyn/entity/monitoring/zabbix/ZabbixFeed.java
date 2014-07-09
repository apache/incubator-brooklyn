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
package brooklyn.entity.monitoring.zabbix;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.feed.AbstractFeed;
import brooklyn.event.feed.AttributePollHandler;
import brooklyn.event.feed.PollHandler;
import brooklyn.event.feed.Poller;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.access.BrooklynAccessUtils;
import brooklyn.location.basic.SupportsPortForwarding;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.net.Cidr;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.net.HostAndPort;
import com.google.gson.JsonObject;

public class ZabbixFeed extends AbstractFeed {

    public static final Logger log = LoggerFactory.getLogger(ZabbixFeed.class);

    public static final String JSON_ITEM_GET =
            "{ \"jsonrpc\":\"2.0\",\"method\":\"item.get\"," +
                "\"params\":{\"output\":\"extend\"," +
                    "\"filter\":{\"hostid\":[\"{{hostId}}\"],\"key_\":\"{{itemKey}}\"}}," +
                "\"auth\":\"{{token}}\",\"id\":{{id}}}";
    public static final String JSON_USER_LOGIN =
            "{ \"jsonrpc\":\"2.0\",\"method\":\"user.login\"," +
                "\"params\":{\"user\":\"{{username}}\",\"password\":\"{{password}}\"}," +
                "\"id\":0 }";
    public static final String JSON_HOST_CREATE =
            "{ \"jsonrpc\":\"2.0\",\"method\":\"host.create\"," +
                "\"params\":{\"host\":\"{{host}}\"," +
                    "\"interfaces\":[{\"type\":1,\"main\":1,\"useip\":1,\"ip\":\"{{ip}}\",\"dns\":\"\",\"port\":\"{{port}}\"}]," +
                    "\"groups\":[{\"groupid\":\"{{groupId}}\"}]," +
                    "\"templates\":[{\"templateid\":\"{{templateId}}\"}]}," +
                "\"auth\":\"{{token}}\",\"id\":{{id}}}";

    private static final AtomicInteger id = new AtomicInteger(0);

    public static Builder<ZabbixFeed, ?> builder() {
        return new ConcreteBuilder();
    }

    private static class ConcreteBuilder extends Builder<ZabbixFeed, ConcreteBuilder> {
    }

    public static class Builder<T extends ZabbixFeed, B extends Builder<T,B>> {
        private EntityLocal entity;
        private Supplier<URI> baseUriProvider;
        private long period = 500;
        private TimeUnit periodUnits = TimeUnit.MILLISECONDS;
        private List<ZabbixPollConfig<?>> polls = Lists.newArrayList();
        private URI baseUri;
        private boolean suspended = false;
        private volatile boolean built;
        private ZabbixServer server;
        private String username;
        private String password;
        private Integer sessionTimeout;
        private Integer groupId;
        private Integer templateId;
        private Function<? super EntityLocal, String> uniqueHostnameGenerator = new Function<EntityLocal, String>() {
            @Override public String apply(EntityLocal entity) {
                Location loc = Iterables.find(entity.getLocations(), Predicates.instanceOf(MachineLocation.class));
                return loc.getId();
            }};

        @SuppressWarnings("unchecked")
        protected B self() {
           return (B) this;
        }

        public B entity(EntityLocal val) {
            this.entity = val;
            return self();
        }
        public B baseUri(Supplier<URI> val) {
            if (baseUri!=null && val!=null)
                throw new IllegalStateException("Builder cannot take both a URI and a URI Provider");
            this.baseUriProvider = val;
            return self();
        }
        public B baseUri(URI val) {
            if (baseUriProvider!=null && val!=null)
                throw new IllegalStateException("Builder cannot take both a URI and a URI Provider");
            this.baseUri = val;
            return self();
        }
        public B baseUrl(URL val) {
            return baseUri(URI.create(val.toString()));
        }
        public B baseUri(String val) {
            return baseUri(URI.create(val));
        }
        public B period(long millis) {
            return period(millis, TimeUnit.MILLISECONDS);
        }
        public B period(long val, TimeUnit units) {
            this.period = val;
            this.periodUnits = units;
            return self();
        }
        public B poll(ZabbixPollConfig<?> config) {
            polls.add(config);
            return self();
        }
        public B suspended() {
            return suspended(true);
        }
        public B suspended(boolean startsSuspended) {
            this.suspended = startsSuspended;
            return self();
        }

        public B server(final ZabbixServer server) {
            this.server = server;
            baseUri(URI.create(server.getConfig(ZabbixServer.ZABBIX_SERVER_API_URL)));
            username(server.getConfig(ZabbixServer.ZABBIX_SERVER_USERNAME));
            password(server.getConfig(ZabbixServer.ZABBIX_SERVER_PASSWORD));
            sessionTimeout(server.getConfig(ZabbixServer.ZABBIX_SESSION_TIMEOUT));
            return self();
        }
        public B username(String username) {
            this.username = username;
            return self();
        }
        public B password(String password) {
            this.password = password;
            return self();
        }
        public B sessionTimeout(Integer sessionTimeout) {
            this.sessionTimeout = sessionTimeout;
            return self();
        }
        public B groupId(Integer groupId) {
            this.groupId = groupId;
            return self();
        }
        public B templateId(Integer templateId) {
            this.templateId = templateId;
            return self();
        }
        public B register(Integer groupId, Integer templateId) {
            this.groupId = groupId;
            this.templateId = templateId;
            return self();
        }
        /**
         * For generating the name to be used when registering the zabbix agent with the zabbix server.
         * When called, guarantees that the entity will have a {@link MachineLocation} (see {@link Entity#getLocations()}).
         * Must return a non-empty string that will be unique across all machines where zabbix agents are installed.
         */
        public B uniqueHostnameGenerator(Function<? super EntityLocal, String> val) {
            this.uniqueHostnameGenerator = checkNotNull(val, "uniqueHostnameGenerator");
            return self();
        }
        
        @SuppressWarnings("unchecked")
        public T build() {
            // If server not set and other config not available, try to obtain from entity config
            if (server == null
                    && (baseUri == null || baseUriProvider == null)
                    && username == null && password == null && sessionTimeout == null) {
                ZabbixServer server = Preconditions.checkNotNull(entity.getConfig(ZabbixMonitored.ZABBIX_SERVER), "The ZABBIX_SERVER config key must be set on the entity");
                server(server);
            }
            // Now create feed
            T result = (T) new ZabbixFeed(this);
            built = true;
            if (suspended) result.suspend();
            result.start();
            return result;
        }
        @Override
        protected void finalize() {
            if (!built) log.warn("ZabbixFeed.Builder created, but build() never called");
        }
    }

    protected static class ZabbixPollIdentifier {
        final String itemName;

        protected ZabbixPollIdentifier(String itemName) {
            this.itemName = checkNotNull(itemName, "itemName");
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(itemName);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ZabbixPollIdentifier)) {
                return false;
            }
            ZabbixPollIdentifier o = (ZabbixPollIdentifier) other;
            return Objects.equal(itemName, o.itemName);
        }
    }

    // Treat as immutable once built
    protected final Set<ZabbixPollConfig<?>> polls = Sets.newLinkedHashSet();

    protected Supplier<URI> baseUriProvider;
    protected Integer groupId, templateId;

    // Flag set when the Zabbix agent is registered for a host
    protected final AtomicBoolean registered = new AtomicBoolean(false);

    private final Function<? super EntityLocal, String> uniqueHostnameGenerator;

    protected ZabbixFeed(final Builder<? extends ZabbixFeed, ?> builder) {
        super(builder.entity);

        baseUriProvider = builder.baseUriProvider;
        if (builder.baseUri!=null) {
            if (baseUriProvider!=null)
                throw new IllegalStateException("Not permitted to supply baseUri and baseUriProvider");
            URI uri = builder.baseUri;
            baseUriProvider = Suppliers.ofInstance(uri);
        }
        checkNotNull(baseUriProvider);

        groupId = checkNotNull(builder.groupId, "Zabbix groupId must be set");
        templateId = checkNotNull(builder.templateId, "Zabbix templateId must be set");

        for (ZabbixPollConfig<?> config : builder.polls) {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            ZabbixPollConfig<?> configCopy = new ZabbixPollConfig(config);
            if (configCopy.getPeriod() < 0) configCopy.period(builder.period, builder.periodUnits);
            polls.add(configCopy);
        }
        
        uniqueHostnameGenerator = checkNotNull(builder.uniqueHostnameGenerator, "uniqueHostnameGenerator");
    }

    @Override
    protected void preStart() {
        log.info("starting zabbix feed for {}", entity);

        // TODO if supplier returns null, we may wish to defer initialization until url available?
        // TODO for https should we really trust all?
        final HttpClient httpClient = HttpTool.httpClientBuilder()
                .trustAll()
                .clientConnectionManager(new ThreadSafeClientConnManager())
                .reuseStrategy(new NoConnectionReuseStrategy())
                .uri(baseUriProvider.get())
                .build();

        // Registration job, calls Zabbix host.create API
        final Callable<HttpToolResponse> registerJob = new Callable<HttpToolResponse>() {
            @Override
            public HttpToolResponse call() throws Exception {
                if (!registered.get()) {
                    // Find the first machine, if available
                    Optional<Location> location = Iterables.tryFind(entity.getLocations(), Predicates.instanceOf(MachineLocation.class));
                    if (!location.isPresent()) {
                        return null; // Do nothing until location is present
                    }
                    MachineLocation machine = (MachineLocation) location.get();

                    String host = uniqueHostnameGenerator.apply(entity);
                    
                    // Select address and port using port-forwarding if available
                    String address = entity.getAttribute(Attributes.ADDRESS);
                    Integer port = entity.getAttribute(ZabbixMonitored.ZABBIX_AGENT_PORT);
                    if (machine instanceof SupportsPortForwarding) {
                        Cidr management = entity.getConfig(BrooklynAccessUtils.MANAGEMENT_ACCESS_CIDR);
                        HostAndPort forwarded = ((SupportsPortForwarding) machine).getSocketEndpointFor(management, port);
                        address = forwarded.getHostText();
                        port = forwarded.getPort();
                    }

                    // Fill in the JSON template and POST it
                    byte[] body = JSON_HOST_CREATE
                            .replace("{{token}}", entity.getConfig(ZabbixMonitored.ZABBIX_SERVER).getAttribute(ZabbixServer.ZABBIX_TOKEN))
                            .replace("{{host}}", host)
                            .replace("{{ip}}", address)
                            .replace("{{port}}", Integer.toString(port))
                            .replace("{{groupId}}", Integer.toString(groupId))
                            .replace("{{templateId}}", Integer.toString(templateId))
                            .replace("{{id}}", Integer.toString(id.incrementAndGet()))
                            .getBytes();
                    
                    return HttpTool.httpPost(httpClient, baseUriProvider.get(), ImmutableMap.of("Content-Type", "application/json"), body);
                }
                return null;
            }
        };

        // The handler for the registration job
        PollHandler<? super HttpToolResponse> registrationHandler = new PollHandler<HttpToolResponse>() {
            @Override
            public void onSuccess(HttpToolResponse val) {
                if (registered.get() || val == null) {
                    return; // Skip if we are registered already or no data from job
                }
                JsonObject response = HttpValueFunctions.jsonContents().apply(val).getAsJsonObject();
                if (response.has("error")) {
                    // Parse the JSON error object and log the message
                    JsonObject error = response.get("error").getAsJsonObject();
                    String message = error.get("message").getAsString();
                    String data = error.get("data").getAsString();
                    log.warn("zabbix failed registering host - {}: {}", message, data);
                } else if (response.has("result")) {
                    // Parse the JSON result object and save the hostId
                    JsonObject result = response.get("result").getAsJsonObject();
                    String hostId = result.get("hostids").getAsJsonArray().get(0).getAsString();
                    // Update the registered status if not set
                    if (registered.compareAndSet(false, true)) {
                        entity.setAttribute(ZabbixMonitored.ZABBIX_AGENT_HOSTID, hostId);
                        log.info("zabbix registered host as id {}", hostId);
                    }
                } else {
                    throw new IllegalStateException(String.format("zabbix host registration returned invalid result: %s", response.toString()));
                }
            }
            @Override
            public boolean checkSuccess(HttpToolResponse val) {
                return (val.getResponseCode() == 200);
            }
            @Override
            public void onFailure(HttpToolResponse val) {
                log.warn("zabbix sever returned failure code: {}", val.getResponseCode());
            }
            @Override
            public void onException(Exception exception) {
                log.warn("zabbix exception registering host", exception);
            }
            @Override
            public String toString() {
                return super.toString()+"["+getDescription()+"]";
            }
            @Override
            public String getDescription() {
                return "Zabbix rest poll";
            }
        };

        // Schedule registration attempt once per second
        getPoller().scheduleAtFixedRate(registerJob, registrationHandler, 1000l); // TODO make configurable

        // Create a polling job for each Zabbix metric
        for (final ZabbixPollConfig<?> config : polls) {
            Callable<HttpToolResponse> pollJob = new Callable<HttpToolResponse>() {
                @Override
                public HttpToolResponse call() throws Exception {
                    if (registered.get()) {
                        if (log.isTraceEnabled()) log.trace("zabbix polling {} for {}", entity, config);
                        byte[] body = JSON_ITEM_GET
                                .replace("{{token}}", entity.getConfig(ZabbixMonitored.ZABBIX_SERVER).getAttribute(ZabbixServer.ZABBIX_TOKEN))
                                .replace("{{hostId}}", entity.getAttribute(ZabbixMonitored.ZABBIX_AGENT_HOSTID))
                                .replace("{{itemKey}}", config.getItemKey())
                                .replace("{{id}}", Integer.toString(id.incrementAndGet()))
                                .getBytes();
                        
                        return HttpTool.httpPost(httpClient, baseUriProvider.get(), ImmutableMap.of("Content-Type", "application/json"), body);
                    } else {
                        throw new IllegalStateException("zabbix agent not yet registered");
                    }
                }
            };

            // Schedule the Zabbix polling job
            AttributePollHandler<? super HttpToolResponse> pollHandler = new AttributePollHandler<HttpToolResponse>(config, entity, this);
            long minPeriod = Integer.MAX_VALUE; // TODO make configurable
            if (config.getPeriod() > 0) minPeriod = Math.min(minPeriod, config.getPeriod());
            getPoller().scheduleAtFixedRate(pollJob, pollHandler, minPeriod);
        }

    }

    @SuppressWarnings("unchecked")
    protected Poller<HttpToolResponse> getPoller() {
        return (Poller<HttpToolResponse>) poller;
    }
}
