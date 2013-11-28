package brooklyn.util.http;

import static org.testng.Assert.assertTrue;

import java.net.URI;

import org.apache.http.client.HttpClient;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.event.feed.http.HttpPollValue;
import brooklyn.location.basic.PortRanges;
import brooklyn.test.HttpService;

import com.google.common.collect.ImmutableMap;

public class HttpToolIntegrationTest {

    // TODO Expand test coverage for credentials etc
    
    private HttpService httpService;

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (httpService != null) httpService.shutdown();
    }
    
    @Test(groups = {"Integration"})
    public void testHttpGet() throws Exception {
        httpService = new HttpService(PortRanges.fromString("9000+"), true).start();
        URI baseUri = new URI(httpService.getUrl());

        HttpClient client = HttpTool.httpClientBuilder().https(true).build();
        HttpPollValue result = HttpTool.httpGet(client, baseUri, ImmutableMap.<String,String>of());
        assertTrue(new String(result.getContent()).contains("Hello, World"), "val="+new String(result.getContent()));
    }
    
    @Test(groups = {"Integration"})
    public void testHttpPost() throws Exception {
        httpService = new HttpService(PortRanges.fromString("9000+"), true).start();
        URI baseUri = new URI(httpService.getUrl());

        HttpClient client = HttpTool.httpClientBuilder().https(true).build();
        HttpPollValue result = HttpTool.httpPost(client, baseUri, ImmutableMap.<String,String>of(), new byte[0]);
        assertTrue(new String(result.getContent()).contains("Hello, World"), "val="+new String(result.getContent()));
    }
}
