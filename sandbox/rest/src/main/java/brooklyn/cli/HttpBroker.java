package brooklyn.cli;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.GZIPContentEncodingFilter;

import javax.ws.rs.core.MediaType;
import java.net.ConnectException;

/**
 * This class is responsible for making REST requests.
 *
 * It encapsulates the retry logic and any other HTTP request
 * related features.
 */
public class HttpBroker {

    private Client httpClient;
    private String endpoint;
    private int retry;

    private static enum RequestType { GET, POST, DELETE };

    public HttpBroker(Client httpClient, String endpoint, int retry) {
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.retry = retry;
    }

    private ClientResponse makeRequestWithRetry(String path, RequestType requestType, String data) throws InterruptedException{
        int retryCount = retry;
        boolean shouldTryAgain = true;
        // Will retry every second for # retry seconds
        while(retryCount>0 && shouldTryAgain) {
            retryCount--;
            shouldTryAgain = false;
            try {
                // Make an HTTP request to the REST server and get back a JSON encoded response
                WebResource webResource = httpClient.resource(endpoint+path);
                switch (requestType) {
                    case GET:
                        return webResource
                                .accept(MediaType.APPLICATION_JSON)
                                .get(ClientResponse.class);
                    case POST:
                        webResource.addFilter(new GZIPContentEncodingFilter(true));
                        return webResource
                                .type(MediaType.APPLICATION_JSON)
                                .post(ClientResponse.class, data);
                    case DELETE:
                        return webResource.delete(ClientResponse.class);
                    default:
                        return null; //strange case this
                }
            } catch(ClientHandlerException e) {
                if(e.getCause().getClass().equals(ConnectException.class)){
                    System.out.println("Connecting to server failed. Retrying ...");
                    shouldTryAgain = true; // need to try again
                    Thread.sleep(1000); // wait for a sec before doing that
                }
            }
        }
        // Looks like the request was not successful, so return null (probably a bad idea!)
        System.out.println("Connecting to server failed. Giving up.");
        return null;
    }

    private ClientResponse makeRequestWithRetry(String path, RequestType requestType) throws InterruptedException{
        return makeRequestWithRetry(path,requestType,"");
    }

    public ClientResponse getWithRetry(String path) throws InterruptedException{
        return makeRequestWithRetry(path,RequestType.GET);
    }

    public ClientResponse postWithRetry(String path, String data) throws InterruptedException{
        return makeRequestWithRetry(path,RequestType.POST,data);
    }

    public ClientResponse deleteWithRetry(String path) throws InterruptedException {
        return makeRequestWithRetry(path,RequestType.DELETE,"");
    }

}