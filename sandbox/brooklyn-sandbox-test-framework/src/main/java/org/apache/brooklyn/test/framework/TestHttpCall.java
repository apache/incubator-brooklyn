package org.apache.brooklyn.test.framework;

import com.google.common.collect.Maps;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.time.Duration;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Entity that makes a HTTP Request and tests the respose
 *
 * @author johnmccabe
 */
@ImplementedBy(value = TestHttpCallImpl.class)
public interface TestHttpCall extends Entity, Startable {

    @SetFromFlag(nullable = false)
    ConfigKey<String> TARGET_URL = ConfigKeys.newStringConfigKey("url", "URL to test");

    @SetFromFlag(nullable = false)
    ConfigKey<Map> ASSERTIONS = ConfigKeys.newConfigKey(Map.class, "assert",
            "Assertions to be evaluated", Maps.newLinkedHashMap());

    ConfigKey<Duration> TIMEOUT = ConfigKeys.newConfigKey(Duration.class, "timeout",
            "The duration to wait for assertion result", new Duration(1L, TimeUnit.SECONDS));

}
