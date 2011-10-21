package brooklyn.gemfire.test.demo;

import java.util.List;
import java.util.Properties;

import com.gemstone.gemfire.cache.AttributesFactory;
import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.CacheFactory;
import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionFactory;
import com.gemstone.gemfire.cache.server.CacheServer;
import com.gemstone.gemfire.cache.util.Gateway;
import com.gemstone.gemfire.cache.util.GatewayHub;
import com.gemstone.gemfire.cache.util.GatewayQueueAttributes;
        
/**
 * This starts a cache and hub, then stops the hub to add a new gateway
 */
public class USHub {

    public static void main(String[] args) throws Exception {
        final boolean write_while_stopped = (args.length > 0) && (args[0].equals("-s"));

        if (write_while_stopped) {
                System.err.println("Writing while stopped");
        }

        Properties p = new Properties();
        p.setProperty("log-level","config");
        p.setProperty("mcast-port","12077");
        
        CacheFactory cacheFactory = new CacheFactory(p);
        Cache cache = cacheFactory.create();

        GatewayHub hub = cache.addGatewayHub("US", 11111);

        
        CacheServer cacheServer = cache.addCacheServer();
        cacheServer.setPort(22222);
        
        AttributesFactory<Object,Object> factory = new AttributesFactory<Object,Object>();
        factory.setDataPolicy(DataPolicy.REPLICATE);
        factory.setEnableGateway(new Boolean(true));
        
        RegionFactory<Object, Object> regionFactory = cache.createRegionFactory(factory.create());
        Region<Object, Object> region = regionFactory.create("trades");
        cacheServer.start();
        System.out.println( "started cache server" );

        hub.start();
        System.out.println("started hub");

        //// CLIENT
        if (write_while_stopped) {
            hub.stop();
            System.out.println( "stopped hub" );
        }

        while (!("US_put").equals(region.get("test"))) {
            // wait
            System.out.println("Hub 1: Waiting for client 1 to put test value.");
            Thread.sleep(1000);
        }

        if (!write_while_stopped) {
            hub.stop();
            System.out.println( "stopped hub" );
        }
                
        ////////END CLIENT

        // END OF FIRST ONE

        Gateway gateway = hub.addGateway("EU");
        gateway.addEndpoint("EU-1", "localhost", 33333);

        GatewayQueueAttributes queueAttributes = new GatewayQueueAttributes();
        //queueAttributes.setDiskStoreName("overflow"); // Fails with IllegalStateException - disk store not found. 
        queueAttributes.setMaximumQueueMemory(50);
        queueAttributes.setBatchSize(100);
        queueAttributes.setBatchTimeInterval(1000);
        
        gateway.setQueueAttributes(queueAttributes);
        System.out.println( "added gateway" );

        gateway.start();
        System.out.println( "started gateway" );
        hub.start();
        System.out.println( "started hub" );
        
    }
}
