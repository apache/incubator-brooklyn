package brooklyn.enricher;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.Sensors;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.util.javalang.AtomicReferences;
import brooklyn.util.javalang.Boxing;
import brooklyn.util.math.MathFunctions;
import brooklyn.util.net.Urls;
import brooklyn.util.text.StringFunctions;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Suppliers;

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

    public static final AttributeSensor<Double> REQUEST_LATENCY_IN_SECONDS_MOST_RECENT = Sensors.newDoubleSensor(
            "web.request.latency.last", "Request latency of most recent call, in seconds");

    public static final AttributeSensor<Double> REQUEST_LATENCY_IN_SECONDS_IN_WINDOW = Sensors.newDoubleSensor(
            "web.request.latency.windowed", "Request latency over time window, in seconds");

    HttpFeed httpFeed = null;
    final Duration period;
    
    final boolean requireServiceUp; 
    final AtomicBoolean serviceUp = new AtomicBoolean(false);
    
    final AttributeSensor<String> urlSensor;
    final Function<String,String> urlPostProcessing;
    final AtomicReference<String> url = new AtomicReference<String>(null);
    final Duration rollupWindowSize;
    
    protected HttpLatencyDetector(Builder builder) {
        this.period = builder.period;
        this.requireServiceUp = builder.requireServiceUp;
        
        if (builder.urlSensor != null) {
            this.urlSensor = builder.urlSensor;
            this.urlPostProcessing = builder.urlPostProcessing;
            if (builder.url != null)
                throw new IllegalStateException("Cannot set URL and UrlSensor");
        } else {
            this.url.set(builder.url);
            this.urlSensor = null;
            this.urlPostProcessing = null;
        }

        this.rollupWindowSize = builder.rollupWindowSize;
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

    protected void initialize() {
        httpFeed = HttpFeed.builder()
                .entity(entity)
                .period(period)
                .baseUri(Suppliers.compose(Urls.stringToUriFunction(), AtomicReferences.supplier(url)))
                .poll(new HttpPollConfig<Double>(REQUEST_LATENCY_IN_SECONDS_MOST_RECENT)
                        .onResult(MathFunctions.divide(HttpValueFunctions.latency(), 1000.0d))
                        .setOnException(null))
                .suspended()
                .build();
    }

    protected void startSubscriptions(EntityLocal entity) {
        if (requireServiceUp) {
            subscribe(entity, Startable.SERVICE_UP, new SensorEventListener<Boolean>() {
                @Override
                public void onEvent(SensorEvent<Boolean> event) {
                    if (AtomicReferences.setIfDifferent(serviceUp, Boxing.unboxSafely(event.getValue(), false))) {
                        log.debug(""+this+" updated on "+event+", "+"enabled="+computeEnablement());
                        updateEnablement();
                    }
                }
            });
        }

        if (urlSensor!=null) {
            subscribe(entity, urlSensor, new SensorEventListener<String>() {
                @Override
                public void onEvent(SensorEvent<String> event) {
                    if (AtomicReferences.setIfDifferent(url, urlPostProcessing.apply(event.getValue()))) {
                        log.debug(""+this+" updated on "+event+", "+"enabled="+computeEnablement());
                        updateEnablement();
                    }
                }
            });
        }
    }

    protected void activateAdditionalEnrichers(EntityLocal entity) {
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
        return (!requireServiceUp || serviceUp.get()) && (url.get()!=null);
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
        Function<String, String> urlPostProcessing = Functions.identity();
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
            return new HttpLatencyDetector(this);
        }
    }
    
}
