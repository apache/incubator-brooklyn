package brooklyn.entity.brooklynnode;

import static org.testng.Assert.assertEquals;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.brooklynnode.BrooklynNodeImpl.DeployBlueprintEffectorBody;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.event.feed.http.JsonFunctions;
import brooklyn.rest.BrooklynRestApiLauncherTestFixture;
import brooklyn.test.HttpTestUtils;
import brooklyn.util.http.HttpTool;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

public class DeployBlueprintTest extends BrooklynRestApiLauncherTestFixture {

    private static final Logger log = LoggerFactory.getLogger(DeployBlueprintTest.class);
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        useServerForTest(newServer());
    }

    @Test
    public void testStartsAppViaEffector() throws Exception {
        URI webConsoleUri = URI.create(getBaseUri());
        
        String id = DeployBlueprintEffectorBody.submitPlan(HttpTool.httpClientBuilder().build(), webConsoleUri, 
            "{ services: [ serviceType: \"java:"+BasicApplication.class.getName()+"\" ] }");

        log.info("got: "+id);
        
        String apps = HttpTestUtils.getContent(webConsoleUri.toString()+"/v1/applications");
        List<String> appType = parseJsonList(apps, ImmutableList.of("spec", "type"), String.class);
        assertEquals(appType, ImmutableList.of(BasicApplication.class.getName()));
        
        String status = HttpTestUtils.getContent(webConsoleUri.toString()+"/v1/applications/"+id+"/entities/"+id+"/sensors/service.status");
        log.info("STATUS: "+status);
    }
    
    private <T> List<T> parseJsonList(String json, List<String> elements, Class<T> clazz) {
        Function<String, List<T>> func = HttpValueFunctions.chain(
                JsonFunctions.asJson(),
                JsonFunctions.forEach(HttpValueFunctions.chain(
                        JsonFunctions.walk(elements),
                        JsonFunctions.cast(clazz))));
        return func.apply(json);
    }

}
