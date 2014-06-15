package brooklyn.location.geo;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.collect.Sets;

public class LocalhostExternalIpLoaderIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(LocalhostExternalIpLoaderIntegrationTest.class);

    @Test(groups = "Integration")
    public void testHostsAgreeOnExternalIp() {
        Set<String> ips = Sets.newHashSet();
        for (String url : LocalhostExternalIpLoader.getIpAddressWebsites()) {
            String ip = LocalhostExternalIpLoader.getIpAddressFrom(url);
            LOG.debug("IP from {}: {}", url, ip);
            ips.add(ip);
        }
        assertEquals(ips.size(), 1, "Expected all IP suppliers to agree on the external IP address of Brooklyn. " +
                "Check logs for source responses. ips=" + ips);
    }

    @Test(groups = "Integration")
    public void testLoadExternalIp() {
        assertNotNull(LocalhostExternalIpLoader.getLocalhostIpWaiting());
    }

}
