package brooklyn.location.geo;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;

public class LocalhostExternalIpLoader {

    public static final Logger LOG = LoggerFactory.getLogger(LocalhostExternalIpLoader.class);

    private static final AtomicBoolean retrievingLocalExternalIp = new AtomicBoolean(false);
    private static final CountDownLatch triedLocalExternalIp = new CountDownLatch(1);
    private static volatile String localExternalIp;

    private static class IpLoader implements Callable<String> {
        private static final Pattern ipPattern = Pattern.compile(
                "\\b((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\b");
        final String url;

        protected IpLoader(String url) {
            this.url = url;
        }

        @Override
        public String call() {
            String response = ResourceUtils.create(LocalhostExternalIpLoader.class)
                    .getResourceAsString(url).trim();
            return postProcessResponse(response);
        }

        String postProcessResponse(String response) {
            Matcher matcher = ipPattern.matcher(response);
            boolean matched = matcher.find();
            if (!matched) {
                LOG.error("No IP address matched in output from {}: {}", url, response);
                return null;
            } else {
                return matcher.group();
            }
        }
    }

    @VisibleForTesting
    static List<String> getIpAddressWebsites() {
        String file = new ResourceUtils(LocalhostExternalIpLoader.class)
                .getResourceAsString("classpath://brooklyn/location/geo/external-ip-address-resolvers.txt");
        List<String> urls = Lists.newArrayList(Splitter.on('\n')
                .omitEmptyStrings()
                .trimResults()
                .split(file));
        Collections.shuffle(urls);
        return urls;
    }

    @VisibleForTesting
    static String getIpAddressFrom(String url) {
        return new IpLoader(url).call();
    }

    /** As {@link #getLocalhostIpWithin(Duration)} but without the time limit cut-off. */
    public static String getLocalhostIp() {
        return doLoad(Optional.<Duration>absent());
    }

    /**
     * Attempts to load the public IP address of localhost, failing if the load
     * does not complete within the given duration.
     * @return The public IP address of localhost
     */
    public static String getLocalhostIpWithin(Duration timeout) {
        return doLoad(Optional.of(timeout));
    }

    /**
     * Requests URLs returned by {@link #getIpAddressWebsites()} until one returns an IP address.
     * The address is assumed to be the external IP address of localhost.
     * @param blockFor The maximum duration to wait for the IP address to be resolved.
     *                 An indefinite way if absent.
     * @return A string in IPv4 format, or null if no such address could be ascertained.
     */
    private static String doLoad(Optional<Duration> blockFor) {
        if (localExternalIp != null) {
            return localExternalIp;
        }

        final List<String> candidateUrls = getIpAddressWebsites();
        if (candidateUrls.isEmpty()) {
            LOG.debug("No candidate URLs to use to determine external IP of localhost");
            return null;
        }

        // do in private thread, otherwise blocks for 30s+ on dodgy network!
        // (we can skip it if someone else is doing it, we have synch lock so we'll get notified)
        if (retrievingLocalExternalIp.compareAndSet(false, true)) {
            new Thread() {
                public void run() {
                    for (String url : candidateUrls) {
                        try {
                            LOG.debug("Looking up external IP of this host from {} in private thread {}", url, Thread.currentThread());
                            localExternalIp = new IpLoader(url).call();
                            LOG.debug("Finished looking up external IP of this host from {} in private thread, result ", url, localExternalIp);
                            break;
                        } catch (Throwable t) {
                            LOG.debug("Unable to look up external IP of this host from {}, probably offline {})", url, t);
                        } finally {
                            retrievingLocalExternalIp.set(false);
                            triedLocalExternalIp.countDown();
                        }
                    }
                }
            }.start();
        }

        try {
            if (blockFor.isPresent()) {
                long millis = blockFor.get().toMilliseconds();
                triedLocalExternalIp.await(millis, TimeUnit.MILLISECONDS);
            } else {
                triedLocalExternalIp.await();
            }
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
        if (localExternalIp == null) {
            throw Throwables.propagate(new IOException("Unable to discover external IP of local machine; response to server timed out (ongoing=" + retrievingLocalExternalIp + ")"));
        }
        LOG.debug("Looked up external IP of this host, result is: {}", localExternalIp);
        return localExternalIp;
    }

}
