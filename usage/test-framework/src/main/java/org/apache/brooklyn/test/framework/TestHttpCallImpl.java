package org.apache.brooklyn.test.framework;

import com.google.api.client.util.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

import static org.apache.brooklyn.util.http.HttpAsserts.*;

/**
 * {@inheritDoc}
 */
public class TestHttpCallImpl extends AbstractTest implements TestHttpCall {

    private static final Logger LOG = LoggerFactory.getLogger(TestHttpCallImpl.class);

    /**
     * {@inheritDoc}
     */
    public void start(Collection<? extends Location> locations) {
        ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);
        final String url = getConfig(TARGET_URL);
        final Map assertions = getConfig(ASSERTIONS);
        final Duration timeout = getConfig(TIMEOUT);
        try {
            checkAssertions(url, ImmutableMap.of("timeout", timeout), assertions);
            sensors().set(SERVICE_UP, true);
            ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
        } catch (Throwable t) {
            LOG.info("Url [{}] test failed", url);
            sensors().set(SERVICE_UP, false);
            ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(t);
        }
    }

    /**
     * Tests HTTP Request reponse matches assertions
     * <p>
     * Supported keys in the <code>assertions</code> {@link Map} include:
     * <ul>
     * <li>string - simple string match
     * <li>regex - uses {@link java.lang.String#matches(String)}, if the url returns a multi-line response you should
     * use the embedded dotall flag expression <code>(?s)</code> in your regex.
     * <li>status - HTTP status code
     * </ul>
     * Wraps the {@link org.apache.brooklyn.util.http.HttpAsserts} immediate assertion methods.
     * <p>
     * See the test/resources directory for examples.
     *
     * @param url        The target URL to be tested
     * @param flags      Passed to {@link org.apache.brooklyn.util.http.HttpAsserts#assertContentEventuallyContainsText(Map, String, String, String...)},
     *                   {@link org.apache.brooklyn.util.http.HttpAsserts#assertContentEventuallyMatches(Map, String, String)},
     *                   {@link org.apache.brooklyn.util.http.HttpAsserts#assertHttpStatusCodeEventuallyEquals(Map, String, int)}
     * @param assertions The map of assertions
     */
    private void checkAssertions(final String url, final Map<String, ?> flags, final Map<?, ?> assertions) {

        for (final Map.Entry<?, ?> entry : assertions.entrySet()) {
            if (Objects.equal(entry.getKey(), "regex")) {
                LOG.info("Testing if url [{}] matches regex [{}]",
                        new Object[]{url, entry.getValue()});
                assertContentEventuallyMatches(flags, url, TypeCoercions.coerce(entry.getValue(), String.class));
            } else if (Objects.equal(entry.getKey(), "string")) {
                LOG.debug("Testing if url [{}] contains string [{}]",
                        new Object[]{url, entry.getValue()});
                assertContentEventuallyContainsText(flags, url, TypeCoercions.coerce(entry.getValue(), String.class));
            } else if (Objects.equal(entry.getKey(), "status")) {
                LOG.debug("Testing if url [{}] returns status code [{}]",
                        new Object[]{url, entry.getValue()});
                assertHttpStatusCodeEventuallyEquals(flags, url, TypeCoercions.coerce(entry.getValue(), Integer.class));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void stop() {
        ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPING);
        sensors().set(SERVICE_UP, false);
    }

    /**
     * {@inheritDoc}
     */
    public void restart() {
        final Collection<Location> locations = Lists.newArrayList(getLocations());
        stop();
        start(locations);
    }

}