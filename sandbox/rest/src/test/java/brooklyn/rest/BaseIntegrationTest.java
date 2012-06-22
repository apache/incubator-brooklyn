package brooklyn.rest;

import com.yammer.dropwizard.logging.Log;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.testng.annotations.BeforeTest;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class BaseIntegrationTest {

  private final static Log LOG = Log.forClass(BaseIntegrationTest.class);

  @BeforeTest
  public static void setUpTestEnv() throws InterruptedException {
    final String[] args = new String[]{"server", "config.sample.yml"};
    Executor executor = Executors.newSingleThreadExecutor();
    executor.execute(new Runnable() {
      @Override
      public void run() {
        try {
          BrooklynService.main(args);
        } catch (Exception e) {
          LOG.error("Exception starting brooklynService", e);
        }
      }
    });

    waitForServerStart();
  }

  private static void waitForServerStart() throws InterruptedException {
    HttpClient httpClient = new DefaultHttpClient();
    TimeUnit.SECONDS.sleep(5);
    final String url = "http://localhost:8080/";
    while (true) {
      HttpGet get = new HttpGet(url);
      try {
        HttpResponse response = httpClient.execute(get);
        // if we have a response => server is up, break
        if (response.getStatusLine().getStatusCode() > 0) break;
        httpClient.getConnectionManager().closeIdleConnections(10, TimeUnit.MILLISECONDS);
        TimeUnit.SECONDS.sleep(1);
      } catch (IOException e) {
        LOG.info("We got exception. Server is not up on url {}", url);
      }
    }
  }

}
