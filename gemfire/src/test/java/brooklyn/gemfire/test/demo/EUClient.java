package brooklyn.gemfire.test.demo;

import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.client.ClientCache;
import com.gemstone.gemfire.cache.client.ClientCacheFactory;
import com.gemstone.gemfire.cache.client.NoAvailableServersException;
import com.sun.tools.corba.se.idl.toJavaPortable.StructGen;

public class EUClient {
    public static void main(String[] args) throws Exception {

        String clientCacheXml = args[0];
        String licenseFile = args[1];
        ClientCache cache = getCache(clientCacheXml,licenseFile);
        Region<Object, Object> data1 = cache.getRegion("/trades");

        System.out.println( "Got cache" );

        boolean done = false;
        while (!done) {
            try {
                if (data1.get("test").equals("US_put")) {
                    System.err.println("# Test Passed");
                }
                done = true;
            } catch (NoAvailableServersException e) {
                System.out.println("EU Client: Waiting for server");
                Thread.sleep(1000);
            } catch (NullPointerException e) {
                System.out.println("NPE: Probably caused by EU Client running before US Client. US sets the value EU tries to get.");
                Thread.sleep(5000);
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
