package example.infinispan;

import org.infinispan.Cache
import org.infinispan.config.Configuration
import org.infinispan.config.GlobalConfiguration
import org.infinispan.manager.DefaultCacheManager
import org.infinispan.manager.EmbeddedCacheManager

public class OneB {
    
    public static void main(String[] args) throws Exception {
        println "starting OneB cache"
        
        GlobalConfiguration gc = GlobalConfiguration.getClusteredDefault();
        Configuration c = new Configuration();
        c.setCacheMode(Configuration.CacheMode.DIST_SYNC);
        c.setL1CacheEnabled(false);
        c.setNumOwners(1)
        
        EmbeddedCacheManager cm = new DefaultCacheManager(gc, c);
        println "B   "+cm.getStatus()+ " ... "+cm.getCacheNames()

        Cache<?, ?> cache = cm.getCache("x");
        println "started OneB cache"
        
        assert cache.size() <= 1;
        println "B cache size: "+cache.size()

        int count = 10;
        while (count>0) {
            println "B-9 "+cm.getStatus()+ " ... "+cm.getCacheNames()
            println "B   "+cache.getStatus()+" ... " + cache.keySet()
            println "B key: "+cache.get("key")
            println "B something serializable, it's: "+cache.get("key2")?.name
            if (cache.get("key")) count--;

//            println "B something not serializable, it's: "+cache.get("key3")?.name
//            println "B something definitely not serializable, it's: "+cache.get("key4")?.name
//            println "B something definitely not serializable, it's: "+cache.get("key4")?.t
            Thread.sleep(300);
            cache = cm.getCache("x");
        }
        cm.stop()
     }
    
    
}
