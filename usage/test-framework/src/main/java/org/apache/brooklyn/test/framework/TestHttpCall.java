package org.apache.brooklyn.test.framework;

import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 * Entity that makes a HTTP Request and tests the respose
 *
 * @author johnmccabe
 */
@ImplementedBy(value = TestHttpCallImpl.class)
public interface TestHttpCall extends BaseTest {

    @SetFromFlag(nullable = false)
    ConfigKey<String> TARGET_URL = ConfigKeys.newStringConfigKey("url", "URL to test");

}
