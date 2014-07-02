package io.brooklyn.camp.test.fixture;

import java.net.URL;

import io.brooklyn.camp.BasicCampPlatform;
import io.brooklyn.camp.CampServer;
import io.brooklyn.camp.test.mock.web.MockWebPlatform;

import org.junit.AfterClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.reporters.Files;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.net.Urls;

import com.fasterxml.jackson.databind.ObjectMapper;

public class AbstractRestResourceTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractRestResourceTest.class);
    
    protected BasicCampPlatform platform;
    protected CampServer server;
    
    @BeforeClass
    public void startServer() {
        platform = new BasicCampPlatform();
        populate();
        
        // new server
        server = new CampServer(platform, "").start();
    }
    
    protected void populate() {
        MockWebPlatform.populate(platform);
    }

    @AfterClass 
    public void stopServer() {
        if (server!=null)
            server.stop();
    }
    
    public String load(String path) {
        try {
            String base = "http://localhost:"+server.getPort();
            String x = path.startsWith(base) ? path : Urls.mergePaths(base, path);
            log.debug("Reading from: "+x);
            String s = Files.streamToString(new URL(x).openStream());
            log.debug("Result from "+x+": "+s);
            return s;
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    public <T> T load(String path, Class<T> type) {
        try {
            String data = load(path);
            return new ObjectMapper().readValue(data, type);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
}
