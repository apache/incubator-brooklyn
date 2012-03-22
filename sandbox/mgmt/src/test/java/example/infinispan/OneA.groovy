package example.infinispan;

import org.infinispan.Cache
import org.infinispan.config.Configuration
import org.infinispan.config.GlobalConfiguration
import org.infinispan.manager.DefaultCacheManager
import org.infinispan.manager.EmbeddedCacheManager

import brooklyn.util.internal.LanguageUtils;

public class OneA {
    
    public static void main(String[] args) throws Exception {
        println "starting OneA"
        
        GlobalConfiguration gc = GlobalConfiguration.getClusteredDefault();
        Configuration c = new Configuration();
        c.setCacheMode(Configuration.CacheMode.DIST_SYNC);
        c.setNumOwners(1)
        c.setL1CacheEnabled(false);
        EmbeddedCacheManager cm = new DefaultCacheManager(gc, c);
        
        Cache<?, ?> cache = cm.getCache("x");
        println "started OneA cache"
        
        cache.put("key", "value");
        assert cache.size() == 1;
        assert cache.containsKey("key");
        Object v = cache.remove("key");
        assert v.equals("value");
        assert cache.isEmpty();
        
        cache.put("key2", new SomethingSerializable())
        println "something serializable, it's: "+cache.get("key2").name

        println "A-3 "+cm.getStatus()+ " ... "+cm.getCacheNames()
        println "A x "+cache.getStatus()+" ... " + cache.keySet()

//        cache.put("key3", new SomethingNotSerializable())
//        println "something not serializable, it's: "+cache.get("key3").name
//
//        cache.put("key4", new SomethingDefinitelyNotSerializable())
//        println "something definitely not serializable, it's: "+cache.get("key4").name
//        println "something definitely not serializable, it's: "+cache.get("key4").t
        
        20.times() {
            cache.put("key", ""+Math.random())
            println "A-9 "+cm.getStatus()+ " ... "+cm.getCacheNames()
            println "A x "+cache.getStatus()+" ... " + cache.keySet()
            println "A key: "+cache.get("key")
            println "A something serializable, it's: "+cache.get("key2")?.name

//            println "A something not serializable, it's: "+cache.get("key3")?.name
//            println "A something definitely not serializable, it's: "+cache.get("key4")?.name
//            println "A something definitely not serializable, it's: "+cache.get("key4")?.t
            Thread.sleep(300);
            cache = cm.getCache("x");
        }
        cm.stop()

     }
    
    
}

class SomethingSerializable implements Serializable {
    String name = "foo"
}
class SomethingNotSerializable {
    String name = "foo"
}
class SomethingDefinitelyNotSerializable {
    String name = "foo"
    Thread t = new Thread(name) {
        public void run() {
            while (true) {
                println "thread running ... $t"
                Thread.sleep(500);
            }
        }
    };
}