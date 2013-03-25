package brooklyn.enricher;

import groovy.time.TimeDuration;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.util.internal.TimeExtras;
import brooklyn.util.javalang.AtomicReferences;
import brooklyn.util.javalang.Boxing;
import brooklyn.util.javalang.Providers;
import brooklyn.util.net.UrlFunctions;

import com.google.common.base.Function;
import com.google.common.base.Functions;

public class HttpLatencyDetector extends AbstractEnricher {

    public static final TimeDuration LATENCY_WINDOW_DEFAULT_PERIOD = TimeExtras.duration(10, TimeUnit.SECONDS);

    public static final AttributeSensor<Double> REQUEST_LATENCY_IN_SECONDS_MOST_RECENT = new BasicAttributeSensor<Double>(Double.class,
            "web.request.latency.last", "Request latency of most recent call, in seconds");

    public static final AttributeSensor<Double> REQUEST_LATENCY_IN_SECONDS_IN_WINDOW
            = new BasicAttributeSensor<Double>(Double.class, "web.request.latency.windowed",
                    "Request latency over time window, in seconds");

    HttpFeed httpFeed = null;

    AtomicBoolean serviceUp = new AtomicBoolean(false);
    
    AttributeSensor<String> urlSensor;
    Function<String,String> urlPostProcessing = Functions.identity();
    AtomicReference<String> url = new AtomicReference<String>(null);
    TimeDuration rollupPeriod = LATENCY_WINDOW_DEFAULT_PERIOD;
    
    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);

        initialize();
        
//        subscribe(entity, Startable.SERVICE_UP, SensorEventListeners.setting(serviceUp).onChange(UpdatableEnablements.updatingEnablement(this)));
        subscribe(entity, Startable.SERVICE_UP, new SensorEventListener<Boolean>() {
            @Override
            public void onEvent(SensorEvent<Boolean> event) {
                if (AtomicReferences.setIfDifferent(serviceUp, Boxing.unboxSafely(event.getValue(), false)))
                    updateEnablement();
            }
        });

        if (urlSensor!=null)
            subscribe(entity, urlSensor, new SensorEventListener<String>() {
                @Override
                public void onEvent(SensorEvent<String> event) {
                    if (AtomicReferences.setIfDifferent(url, urlPostProcessing.apply(event.getValue())))
                        updateEnablement();
                }
            });
        
        if (rollupPeriod!=null) {
            entity.addEnricher(new RollingTimeWindowMeanEnricher<Double>(entity,
                REQUEST_LATENCY_IN_SECONDS_MOST_RECENT, REQUEST_LATENCY_IN_SECONDS_IN_WINDOW,
                rollupPeriod.toMilliseconds()));
        }
    }

    public void initialize() {
        httpFeed = HttpFeed.builder()
                .entity(entity)
                .period(1000)
                .baseUri(Providers.transform(Providers.atomic(url), UrlFunctions.URI_FROM_STRING))
                .poll(new HttpPollConfig<Double>(REQUEST_LATENCY_IN_SECONDS_MOST_RECENT)
                        .onSuccess(HttpValueFunctions.latency())
                        .onError(Functions.constant((Double)null)))
                .suspended()
                .build();
    }

    public void updateEnablement() {
        boolean enabled = serviceUp.get() && url.get()!=null;
        if (enabled) {
            httpFeed.resume();
        } else {
            httpFeed.suspend();
        }
    }
    
    @Override
    public void destroy() {
        super.destroy();
        httpFeed.stop();
    }

    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        String url;
        AttributeSensor<String> urlSensor;
        Function<String, String> urlPostProcessing;
        TimeDuration rollupWindowSize = LATENCY_WINDOW_DEFAULT_PERIOD;
        
        public Builder url(String url) {
            this.url = url;
            return this;
        }
        public Builder url(AttributeSensor<String> sensor) {
            this.urlSensor = sensor;
            return this;
        }
        public Builder url(AttributeSensor<String> sensor, Function<String, String> postProcessing) {
            this.urlSensor = sensor;
            this.urlPostProcessing = postProcessing;
            return this;
        }

        public Builder rollup(int windowSize, TimeUnit unit) {
            this.rollupWindowSize = TimeExtras.duration(windowSize, unit);
            return this;
        }
        public Builder rollupOff() {
            this.rollupWindowSize = null;
            return this;
        }
        
        public HttpLatencyDetector build() {
            HttpLatencyDetector result = new HttpLatencyDetector();
            
            if (urlSensor!=null) {
                result.urlSensor = urlSensor;
                if (urlPostProcessing!=null) result.urlPostProcessing = urlPostProcessing;
                if (url!=null)
                    throw new IllegalStateException("Cannot set URL and UrlSensor");
            } else {
                result.url.set(url);
            }

            result.rollupPeriod = rollupWindowSize;
            
            return result;
        }
    }
    
}
