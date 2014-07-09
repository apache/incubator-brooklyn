package brooklyn.entity.nosql.elasticsearch;

import static org.testng.Assert.assertEquals;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.client.methods.HttpGet;
import org.bouncycastle.util.Strings;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class ElasticSearchNodeIntegrationTest {
    
    protected TestApplication app;
    protected Location testLocation;
    protected ElasticSearchNode elasticSearchNode;

    @BeforeMethod(alwaysRun = true)
    public void setup() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        testLocation = new LocalhostMachineProvisioningLocation();
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        Entities.destroyAll(app.getManagementContext());
    }
    
    @Test(groups = {"Integration"})
    public void testStartupAndShutdown() {
        elasticSearchNode = app.createAndManageChild(EntitySpec.create(ElasticSearchNode.class));
        app.start(ImmutableList.of(testLocation));
        
        EntityTestUtils.assertAttributeEqualsEventually(elasticSearchNode, Startable.SERVICE_UP, true);
        
        elasticSearchNode.stop();
        
        EntityTestUtils.assertAttributeEqualsEventually(elasticSearchNode, Startable.SERVICE_UP, false);
    }
    
    @Test(groups = {"Integration"})
    public void testDocumentCount() throws URISyntaxException {
        elasticSearchNode = app.createAndManageChild(EntitySpec.create(ElasticSearchNode.class));
        app.start(ImmutableList.of(testLocation));
        
        EntityTestUtils.assertAttributeEqualsEventually(elasticSearchNode, Startable.SERVICE_UP, true);
        
        EntityTestUtils.assertAttributeEquals(elasticSearchNode, ElasticSearchNode.DOCUMENT_COUNT, 0);
        
        String baseUri = "http://" + elasticSearchNode.getAttribute(Attributes.HOSTNAME) + ":" + elasticSearchNode.getAttribute(Attributes.HTTP_PORT);
        
        HttpToolResponse pingResponse = HttpTool.execAndConsume(
                HttpTool.httpClientBuilder().build(),
                new HttpGet(baseUri));
        assertEquals(pingResponse.getResponseCode(), 200);
        
        String document = "{\"foo\" : \"bar\",\"baz\" : \"quux\"}";
        
        HttpToolResponse putResponse = HttpTool.httpPut(
                HttpTool.httpClientBuilder()
                    .port(elasticSearchNode.getAttribute(Attributes.HTTP_PORT))
                    .build(), 
                new URI(baseUri + "/mydocuments/docs/1"), 
                ImmutableMap.<String, String>of(), 
                Strings.toByteArray(document)); 
        assertEquals(putResponse.getResponseCode(), 201);
        
        HttpToolResponse getResponse = HttpTool.execAndConsume(
                HttpTool.httpClientBuilder().build(),
                new HttpGet(baseUri + "/mydocuments/docs/1/_source"));
        assertEquals(getResponse.getResponseCode(), 200);
        assertEquals(HttpValueFunctions.jsonContents("foo", String.class).apply(getResponse), "bar");
        
        EntityTestUtils.assertAttributeEqualsEventually(elasticSearchNode, ElasticSearchNode.DOCUMENT_COUNT, 1);
    }
}
