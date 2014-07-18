package brooklyn.entity.software.http;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.effector.AddSensor;
import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.time.Duration;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;

import java.util.Map;

/**
 * Configurable {@link brooklyn.entity.proxying.EntityInitializer} which adds an HTTP sensor feed to retrieve the
 * <code>JSONObject</code> from a JSON response in order to populate the sensor with the indicated <code>name</code>.
 */
@Beta
public final class HttpRequestSensor<T> extends AddSensor<T, AttributeSensor<T>> {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(HttpRequestSensor.class);

    public static final ConfigKey<String> JSON_PATH = ConfigKeys.newStringConfigKey("jsonPath");
    public static final ConfigKey<String> SENSOR_URI = ConfigKeys.newStringConfigKey("uri");

    private final String jsonPath;
    private final String uri;

    public HttpRequestSensor(Map<String, String> params) {
        this(ConfigBag.newInstance(params));
    }

    public HttpRequestSensor(ConfigBag params) {
        super(AddSensor.<T>newSensor(params));
        jsonPath = Preconditions.checkNotNull(params.get(JSON_PATH), JSON_PATH);
        uri = Preconditions.checkNotNull(params.get(SENSOR_URI), SENSOR_URI);
    }

    @Override
    public void apply(final EntityLocal entity) {
        super.apply(entity);

        Duration period = Duration.ONE_SECOND;

        HttpPollConfig<T> pollConfig = new HttpPollConfig<T>(sensor)
                .checkSuccess(HttpValueFunctions.responseCodeEquals(200))
                .onFailureOrException(Functions.constant((T) null))
                .onSuccess(HttpValueFunctions.<T>jsonContentsFromPath(jsonPath))
                .period(period);

        HttpFeed.builder().entity(entity)
                .baseUri(uri)
                .poll(pollConfig)
                .build();
    }
}
