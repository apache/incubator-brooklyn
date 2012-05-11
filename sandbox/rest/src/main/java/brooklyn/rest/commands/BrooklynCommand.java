package brooklyn.rest.commands;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.sun.jersey.api.client.filter.GZIPContentEncodingFilter;
import com.sun.jersey.client.apache4.ApacheHttpClient4Handler;
import com.sun.jersey.client.apache4.config.ApacheHttpClient4Config;
import com.sun.jersey.client.apache4.config.DefaultApacheHttpClient4Config;
import com.yammer.dropwizard.AbstractService;
import com.yammer.dropwizard.cli.Command;
import com.yammer.dropwizard.client.HttpClientFactory;
import com.yammer.dropwizard.client.JerseyClient;
import com.yammer.dropwizard.client.JerseyClientConfiguration;
import com.yammer.dropwizard.jersey.JacksonMessageBodyProvider;
import com.yammer.dropwizard.json.Json;
import com.yammer.dropwizard.util.Duration;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.http.client.HttpClient;

public abstract class BrooklynCommand extends Command {

  private String endpoint;

  /**
   * Create a new {@link BrooklynCommand} instance.
   *
   * @param name        the command name (must be unique for the service)
   * @param description the description of the command
   */
  protected BrooklynCommand(String name, String description) {
    super(name, description);
  }

  @Override
  public Options getOptions() {
    return super.getOptions()
        .addOption("e", "endpoint", true, "Server endpoint");
  }

  @Override
  protected void run(AbstractService<?> service, CommandLine params) throws Exception {
    JerseyClientConfiguration config = new JerseyClientConfiguration();
    config.setTimeout(Duration.seconds(2));

    endpoint = getEndpointFromCommandLineOrEnv(params);

    try {
      run(service.getJson(), buildJerseyClient(service, config), params);

    } catch (Exception e) {
      System.err.println(e.getMessage());
      System.exit(-1);
    }
  }

  /**
   * Override this method to implement command functionality
   */
  protected abstract void run(Json json, JerseyClient client, CommandLine params) throws Exception;

  private String getEndpointFromCommandLineOrEnv(CommandLine params) {
    String endpointFromEnv = System.getenv("BROOKLYN_ENDPOINT");

    // the command line has precedence over the environment
    return params.getOptionValue("endpoint",
        (endpointFromEnv != null) ? endpointFromEnv : "http://localhost:8080");

  }

  protected URI uriFor(String resource) {
    return URI.create(endpoint + resource);
  }

  protected URI expandIfRelative(URI uri) {
    if (uri.getHost() != null) {
      return uri;
    }
    return URI.create(endpoint + uri.getPath());
  }

  protected JerseyClient buildJerseyClient(AbstractService<?> service, JerseyClientConfiguration configuration) {

    final HttpClient client = new HttpClientFactory(configuration).build();
    final ApacheHttpClient4Handler handler = new ApacheHttpClient4Handler(client, null, true);

    final ApacheHttpClient4Config config = new DefaultApacheHttpClient4Config();
    config.getSingletons().add(new JacksonMessageBodyProvider(service.getJson()));

    final JerseyClient jerseyClient = new JerseyClient(handler, config);
    jerseyClient.setExecutorService(buildThreadPool(configuration));

    if (configuration.isGzipEnabled()) {
      jerseyClient.addFilter(new GZIPContentEncodingFilter());
    }

    return jerseyClient;
  }

  private ExecutorService buildThreadPool(JerseyClientConfiguration configuration) {
    final ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(true)
        .setNameFormat("jersey-client-%d")
        .build();

    return new ThreadPoolExecutor(configuration.getMinThreads(),
        configuration.getMaxThreads(),
        60, TimeUnit.SECONDS,
        new SynchronousQueue<Runnable>(),
        threadFactory);
  }

}
