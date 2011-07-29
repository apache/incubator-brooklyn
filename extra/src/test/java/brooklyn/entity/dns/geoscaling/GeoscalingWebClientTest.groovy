package brooklyn.entity.dns.geoscaling

import static brooklyn.entity.dns.geoscaling.GeoscalingWebClient.PROVIDE_CITY_INFO;
import static brooklyn.entity.dns.geoscaling.GeoscalingWebClient.PROVIDE_COUNTRY_INFO;
import static brooklyn.entity.dns.geoscaling.GeoscalingWebClient.PROVIDE_EXTRA_INFO;
import static brooklyn.entity.dns.geoscaling.GeoscalingWebClient.PROVIDE_NETWORK_INFO;
import static brooklyn.entity.dns.geoscaling.GeoscalingWebClient.PROVIDE_UPTIME_INFO;
import static org.testng.AssertJUnit.*

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashSet;
import java.util.Set;

import org.testng.annotations.Test;

import brooklyn.entity.dns.HostGeoInfo;
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.Domain;
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.SmartSubdomain;

/**
 * {@link GeoscalingWebClient} unit tests.
 */
class GeoscalingWebClientTest {
    
    private final static String USERNAME = "cloudsoft";
    private final static String PASSWORD = "cl0uds0ft";
    private final static String PRIMARY_DOMAIN = "domain"+((int)(Math.random()*10000))+".test.org";
    private final static String SUBDOMAIN = "subdomain"+((int)(Math.random()*10000));
    private final static String DEFAULT_SCRIPT = 'output[] = array("fail");'
    
    
    @Test(groups = "Integration")
    public void testWebClient() {
        GeoscalingWebClient geoscaling = new GeoscalingWebClient();
        geoscaling.login(USERNAME, PASSWORD);
        
        assertNull(geoscaling.getPrimaryDomain(PRIMARY_DOMAIN));
        geoscaling.createPrimaryDomain(PRIMARY_DOMAIN);
        Domain domain = geoscaling.getPrimaryDomain(PRIMARY_DOMAIN);
        assertNotNull(domain);
        
        assertNull(domain.getSmartSubdomain(SUBDOMAIN));
        domain.createSmartSubdomain(SUBDOMAIN);
        SmartSubdomain smartSubdomain = domain.getSmartSubdomain(SUBDOMAIN);
        assertNotNull(smartSubdomain);
        
        smartSubdomain.configure(
            DEFAULT_SCRIPT,
            PROVIDE_NETWORK_INFO | PROVIDE_CITY_INFO | PROVIDE_COUNTRY_INFO | PROVIDE_EXTRA_INFO | PROVIDE_UPTIME_INFO);
        
        // TODO: read-back config and verify is as expected?
        // TODO: send actual config, test ping/dig from multiple locations?
        // TODO: rename subdomain
        
        smartSubdomain.delete();
        assertNull(domain.getSmartSubdomain(SUBDOMAIN));
        domain.delete();
        assertNull(geoscaling.getPrimaryDomain(PRIMARY_DOMAIN));
        
        geoscaling.logout();
    }
    
}
