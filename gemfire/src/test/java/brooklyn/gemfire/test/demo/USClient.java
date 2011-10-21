package brooklyn.gemfire.test.demo;

import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.client.ClientCache;
import com.gemstone.gemfire.cache.client.ClientCacheFactory;
import com.gemstone.gemfire.cache.client.NoAvailableServersException;

public class USClient {
    public static void main(String[] args) throws Exception {

        String clientCacheXml = args[0];
        String licenseFile = args[1];
        ClientCache cache = getCache(clientCacheXml,licenseFile);
        
        Region<Object, Object> data1 = cache.getRegion("/trades");

        System.out.println( "Got cache for: " +clientCacheXml );

        boolean done = false;
        while (!done) {
            try {
                data1.put("test", "US_put");
                System.out.println( "Put test value" );
                done = true;
            } catch (NoAvailableServersException e) {
                System.out.println("US Client: Waiting for server");
                Thread.sleep(1000);
            }
        }

        cache.close();
        System.out.println( "Closed cache" );
        
    }

    private static ClientCache getCache( String clientCacheXml, String licenseFile ) {
        return new ClientCacheFactory().set("cache-xml-file", clientCacheXml)
                                       .set("license-file",licenseFile)
                                       .create();
    }
}
