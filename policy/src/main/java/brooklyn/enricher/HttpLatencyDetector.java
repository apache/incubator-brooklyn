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
package brooklyn.enricher;

import static com.google.common.base.Preconditions.checkState;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.Sensors;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.javalang.AtomicReferences;
import brooklyn.util.javalang.Boxing;
import brooklyn.util.math.MathFunctions;
import brooklyn.util.net.Urls;
import brooklyn.util.text.StringFunctions;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Suppliers;
import com.google.common.reflect.TypeToken;

/**
 * An Enricher which computes latency in accessing a URL. 
 * See comments on the methods in the static {@link #builder()} this exposes.
 * <p>
 * This is currently tested against relatively simple GET requests,
 * optionally returned from sensors.  It does not currently support POST 
 * and has limited support for https.
 */
public class HttpLatencyDetector extends AbstractEnricher {

    private static final Logger log = LoggerFactory.getLogger(HttpLatencyDetector.class);
    
    public static final Duration LATENCY_WINDOW_DEFAULT_PERIOD = Duration.TEN_SECONDS;

    @SetFromFlag("url")
    public static final ConfigKey<String> URL = ConfigKeys.newStringConfigKey("latencyDetector.url");
    
    @SetFromFlag("urlSensor")
    public static final ConfigKey<AttributeSensor<String>> URL_SENSOR = ConfigKeys.newConfigKey(new TypeToken<AttributeSensor<String>>() {}, "latencyDetector.urlSensor");

    @SetFromFlag("urlPostProcessing")
    public static final ConfigKey<Function<String,String>> URL_POST_PROCESSING = ConfigKeys.newConfigKey(
            new TypeToken<Function<String,String>>() {}, 
            "latencyDetector.urlPostProcessing",
            "Function applied to the urlSensor value, to determine the URL to use");

    @SetFromFlag("rollup")
    public static final ConfigKey<Duration> ROLLUP_WINDOW_SIZE = ConfigKeys.newConfigKey(Duration.class, "latencyDetector.rollup");

    @SetFromFlag("requireServiceUp")
    public static final ConfigKey<Boolean> REQUIRE_SERVICE_UP = ConfigKeys.newBooleanConfigKey("latencyDetector.requireServiceUp");

    @SetFromFlag("period")
    public static final ConfigKey<Duration> PERIOD = ConfigKeys.newConfigKey(Duration.class, "latencyDetector.period");

    public static final AttributeSensor<Double> REQUEST_LATENCY_IN_SECONDS_MOST_RECENT = Sensors.newDoubleSensor(
            "web.request.latency.last", "Request latency of most recent call, in seconds");

    public static final AttributeSensor<Double> REQUEST_LATENCY_IN_SECONDS_IN_WINDOW = Sensors.newDoubleSensor(
            "web.request.latency.windowed", "Request latency over time window, in seconds");

    final AtomicBoolean serviceUp = new AtomicBoolean(false);
    final AtomicReference<String> url = new AtomicReference<String>();
    HttpFeed httpFeed = null;

    public HttpLatencyDetector() { // for rebinding, and for EnricherSpec usage
    }
    
    protected HttpLatencyDetector(Map flags) {
        super(flags);
    }
    
    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);

        initialize();
        startSubscriptions(entity);
        activateAdditionalEnrichers(entity);
        
        if (log.isDebugEnabled())
            log.debug(""+this+" enabled="+computeEnablement()+" when attached, subscribing to "+getAllSubscriptions());
        
        updateEnablement();
    }

    // TODO use init()?
    protected void initialize() {
        checkState(getConfig(URL) != null ^ getConfig(URL_SENSOR) != null, 
                "Must set exactly one of url or urlSensor: url=%s; urlSensor=%s", getConfig(URL), getConfig(URL_SENSOR));
        checkState(getConfig(URL_SENSOR) != null || getConfig(URL_POST_PROCESSING) == null, 
                "Must not set urlPostProcessing without urlSensor");
        
        url.set(getConfig(URL));
        
        httpFeed = HttpFeed.builder()
                .entity(entity)
                .period(getConfig(PERIOD))
                .baseUri(Suppliers.compose(Urls.stringToUriFunction(), AtomicReferences.supplier(url)))
                .poll(new HttpPollConfig<Double>(REQUEST_LATENCY_IN_SECONDS_MOST_RECENT)
                        .onResult(MathFunctions.divide(HttpValueFunctions.latency(), 1000.0d))
                        .setOnException(null))
                .suspended()
                .build();
    }

    protected void startSubscriptions(EntityLocal entity) {
        if (getConfig(REQUIRE_SERVICE_UP)) {
            subscribe(entity, Startable.SERVICE_UP, new SensorEventListener<Boolean>() {
                @Override
                public void onEvent(SensorEvent<Boolean> event) {
                    if (AtomicReferences.setIfDifferent(serviceUp, Boxing.unboxSafely(event.getValue(), false))) {
                        log.debug(""+this+" updated on "+event+", "+"enabled="+computeEnablement());
                        updateEnablement();
                    }
                }
            });
            
            // TODO would be good if subscription gave us the current value, rather than risking a race with code like this.
            Boolean currentVal = entity.getAttribute(Startable.SERVICE_UP);
            if (currentVal != null) {
                AtomicReferences.setIfDifferent(serviceUp, currentVal);
            }
        }
        
        AttributeSensor<String> urlSensor = getConfig(URL_SENSOR);
        if (urlSensor!=null) {
            subscribe(entity, urlSensor, new SensorEventListener<String>() {
                @Override
                public void onEvent(SensorEvent<String> event) {
                    Function<String, String> postProcessor = getConfig(URL_POST_PROCESSING);
                    String val = event.getValue();
                    String newVal = (postProcessor != null) ? postProcessor.apply(val) : val;
                    if (AtomicReferences.setIfDifferent(url, newVal)) {
                        log.debug(""+this+" updated on "+event+", "+"enabled="+computeEnablement());
                        updateEnablement();
                    }
                }
            });
            
            // TODO would be good if subscription gave us the current value, rather than risking a race with code like this.
            String currentVal = entity.getAttribute(urlSensor);
            if (currentVal != null) {
                Function<String, String> postProcessor = getConfig(URL_POST_PROCESSING);
                String newVal = (postProcessor != null) ? postProcessor.apply(currentVal) : currentVal;
                if (AtomicReferences.setIfDifferent(url, newVal)) {
                    log.debug("{} updated url on initial connectionon, to {}", this, newVal);
                }
            }
        }
    }

    protected void activateAdditionalEnrichers(EntityLocal entity) {
        Duration rollupWindowSize = getConfig(ROLLUP_WINDOW_SIZE);
        if (rollupWindowSize!=null) {
            entity.addEnricher(new RollingTimeWindowMeanEnricher<Double>(entity,
                REQUEST_LATENCY_IN_SECONDS_MOST_RECENT, REQUEST_LATENCY_IN_SECONDS_IN_WINDOW,
                rollupWindowSize));
        }
    }

    /** refreshes whether the latency detection feed should be enabled,
     * based on e.g. service up, the URL sensor, etc */
    public void updateEnablement() {
        if (computeEnablement()) {
            httpFeed.resume();
        } else {
            httpFeed.suspend();
        }
    }

    protected boolean computeEnablement() {
        return (!getConfig(REQUIRE_SERVICE_UP) || serviceUp.get()) && (url.get()!=null);
    }
    
    @Override
    public void destroy() {
        super.destroy();
        if (httpFeed != null) httpFeed.stop();
    }

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        boolean requireServiceUp = true;
        Duration period = Duration.ONE_SECOND;
        String url;
        AttributeSensor<String> urlSensor;
        Function<String, String> urlPostProcessing;
        Duration rollupWindowSize = LATENCY_WINDOW_DEFAULT_PERIOD;
        
        /** indicates that the HttpLatencyDetector should not require "service up";
         * if using this, you must supply a URL sensor or the detector will not know when to run */
        // or extend this Builder and the Detector to allow subscribing to additional sensors
        public Builder noServiceUp() {
            requireServiceUp = false;
            return this;
        }

        /** sets how often to test for latency */
        public Builder period(Duration period) {
            this.period = period;
            return this;
        }
        public Builder period(int amount, TimeUnit unit) {
            return period(Duration.of(amount, unit));
        }
        
        /** supplies a constant URL which should be polled for latency,
         * where this constant URL is known. typically the alternative
         * {@link #url(AttributeSensor)} is used (but you cannot use both forms) */
        public Builder url(String url) {
            this.url = url;
            return this;
        }
        /** @see #url(String) */
        public Builder url(URL url) {
            return url(url.toString());
        }
        /** supplies a sensor which indicates the URL this should parse (e.g. ROOT_URL) */
        public Builder url(AttributeSensor<String> sensor) {
            this.urlSensor = sensor;
            return this;
        }
        /** supplies a sensor which indicates the URL which this should parse (e.g. ROOT_URL),
         * with post-processing, e.g. {@link StringFunctions#append(String)} */
        public Builder url(AttributeSensor<String> sensor, Function<String, String> postProcessing) {
            this.urlSensor = sensor;
            this.urlPostProcessing = postProcessing;
            return this;
        }

        /** specifies a size of the time window which should be used to give a rolled-up average;
         * defaults to {@link HttpLatencyDetector#LATENCY_WINDOW_DEFAULT_PERIOD} */ 
        public Builder rollup(Duration windowSize) {
            this.rollupWindowSize = windowSize;
            return this;
        }
        
        /** see {@link #rollup(Duration)} */
        public Builder rollup(int windowSize, TimeUnit unit) {
            return rollup(Duration.of(windowSize, unit));
        }
        
        /** specifies that a rolled-up average should _not_ be calculated and emitted 
         * (defaults to true) */
        public Builder rollupOff() {
            this.rollupWindowSize = null;
            return this;
        }

        /** returns the detector. note that callers should then add this to the entity,
         * typically using {@link Entity#addEnricher(brooklyn.policy.Enricher)} */
        public HttpLatencyDetector build() {
            return new HttpLatencyDetector(MutableMap.builder()
                    .putIfNotNull(PERIOD, period)
                    .putIfNotNull(ROLLUP_WINDOW_SIZE, rollupWindowSize)
                    .putIfNotNull(REQUIRE_SERVICE_UP, requireServiceUp)
                    .putIfNotNull(URL, url)
                    .putIfNotNull(URL_SENSOR, urlSensor)
                    .putIfNotNull(URL_POST_PROCESSING, urlPostProcessing)
                    .build());
        }
    }
}
