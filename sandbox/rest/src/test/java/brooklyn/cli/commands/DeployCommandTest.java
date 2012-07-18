package brooklyn.cli.commands;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import brooklyn.rest.api.Application;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URL;

public class DeployCommandTest {

    @Test(enabled = true)
    public void testInferFormatClass() throws Exception {
        DeployCommand cmd = new DeployCommand();
        cmd.app = "brooklyn.cli.MyTestApp";
        assertEquals(cmd.inferAppFormat(cmd.app), DeployCommand.CLASS_FORMAT);
    }

    @Test(enabled = true)
    public void testInferFormatGroovy() throws Exception {
        DeployCommand cmd = new DeployCommand();
        cmd.app = "/my/path/my.groovy";
        assertEquals(cmd.format, DeployCommand.GROOVY_FORMAT);
    }

    @Test(enabled = true)
    public void testInferFormatJson() throws Exception {
        DeployCommand cmd = new DeployCommand();
        cmd.app = "/my/path/my.json";
        assertEquals(cmd.format, DeployCommand.JSON_FORMAT);

        DeployCommand cmd2 = new DeployCommand();
        cmd2.app = "/my/path/my.jsn";
        assertEquals(cmd.format, DeployCommand.JSON_FORMAT);
    }

    @Test(enabled = true)
    public void testFormatOverridesInference() throws Exception {
        DeployCommand cmd = new DeployCommand();
        cmd.format = DeployCommand.JSON_FORMAT;
        cmd.app = "/my/silly/my.groovy";
        assertEquals(cmd.format, DeployCommand.JSON_FORMAT);
    }

    public void testFoo2() throws Exception {
        MockWebServer server = new MockWebServer();
        for (int i = 0; i < 100; i++) {
            server.enqueue(new MockResponse().setResponseCode(200).addHeader("content-type: application/json").setBody("{\"foo\":\"myfoo\"}"));
        }
        server.play();

        URL baseUrl = server.getUrl("/");

        String appLocation = "whoat?";

        DeployCommand cmd = new DeployCommand();
        cmd.format = DeployCommand.CLASS_FORMAT;
        cmd.app = "MyTestApp";
        cmd.setOut(new PrintStream(out));
        cmd.run();

        String outstr = new String(out.toByteArray());
        assertTrue(outstr.contains("The application has been deployed: "+appLocation), "outstr="+outstr);

        server.takeRequest();
    }

    @Test(enabled=false)
    public void testFoo() throws Exception {
        String appLocation = "myapplication";
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        final Client jerseyClient = Mockito.mock(Client.class);
        WebResource webResource = Mockito.mock(WebResource.class);
        WebResource.Builder webResourceBuilder = Mockito.mock(WebResource.Builder.class);
        ClientResponse clientResponse = Mockito.mock(ClientResponse.class);

        WebResource webResource2 = Mockito.mock(WebResource.class);
        WebResource.Builder webResourceBuilder2 = Mockito.mock(WebResource.Builder.class);
        ClientResponse clientResponse2 = Mockito.mock(ClientResponse.class);

        Mockito.when(jerseyClient.resource("/v1/applications/MyTestApp")).thenReturn(webResource);
        Mockito.when(webResource.type(MediaType.APPLICATION_JSON)).thenReturn(webResourceBuilder);
        Mockito.when(webResourceBuilder.post(ClientResponse.class, "{app:\"MyTestApp\"}")).thenReturn(clientResponse);
        Mockito.when(clientResponse.getStatus()).thenReturn(Response.Status.CREATED.getStatusCode());
        Mockito.when(clientResponse.getLocation()).thenReturn(URI.create(appLocation));

        Mockito.when(jerseyClient.resource(appLocation)).thenReturn(webResource2);
        Mockito.when(webResource2.type(MediaType.APPLICATION_JSON)).thenReturn(webResourceBuilder2);
        Mockito.when(webResourceBuilder2.post(ClientResponse.class)).thenReturn(clientResponse2);
        Mockito.when(clientResponse2.getEntity(String.class)).thenReturn("{app:\"RUNNING\"}");

        DeployCommand cmd = new DeployCommand() {
            public Client getClient() {
                return jerseyClient;
            }
        };

        cmd.format = DeployCommand.CLASS_FORMAT;
        cmd.app = "MyTestApp";
        cmd.setOut(new PrintStream(out));
        cmd.run();

        String outstr = new String(out.toByteArray());
        assertTrue(outstr.contains("The application has been deployed: "+appLocation), "outstr="+outstr);
    }
}
