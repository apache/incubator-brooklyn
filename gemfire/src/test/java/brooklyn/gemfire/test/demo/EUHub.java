package brooklyn.gemfire.test.demo;

import java.util.Properties;

import com.gemstone.gemfire.cache.AttributesFactory;
import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.CacheFactory;
import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.RegionFactory;
import com.gemstone.gemfire.cache.server.CacheServer;
import com.gemstone.gemfire.cache.util.GatewayHub;
        
/**
 * This starts a cache and hub, then stops the hub to add a new gateway
 */
public class EUHub {

    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        p.setProperty("log-level","config");
        p.setProperty("mcast-port","12077");
        
        CacheFactory cacheFactory = new CacheFactory(p);
        Cache cache = cacheFactory.create();
        
        GatewayHub hub = cache.addGatewayHub("EU", 33333);
        
        CacheServer cacheServer = cache.addCacheServer();
        cacheServer.setPort(44444);
        
        AttributesFactory<Object,Object> factory = new AttributesFactory<Object,Object>();
        factory.setDataPolicy(DataPolicy.REPLICATE);
        factory.setEnableGateway(new Boolean(true));

        cacheServer.start();
        System.out.println( "started cache server" );

        hub.start();
        System.out.println("started hub, waiting for input...");
        
        System.in.read();
        System.out.println("creating region");
        RegionFactory<Object, Object> regionFactory = cache.createRegionFactory(factory.create());
        regionFactory.create("trades");
    }
}