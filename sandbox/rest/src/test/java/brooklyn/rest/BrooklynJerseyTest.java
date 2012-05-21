package brooklyn.rest;

import com.sun.jersey.test.framework.AppDescriptor;
import com.sun.jersey.test.framework.JerseyTest;
import com.sun.jersey.test.framework.LowLevelAppDescriptor;
import com.yammer.dropwizard.bundles.JavaBundle;
import com.yammer.dropwizard.jersey.DropwizardResourceConfig;
import com.yammer.dropwizard.jersey.JacksonMessageBodyProvider;
import com.yammer.dropwizard.json.Json;
import java.net.URI;
import java.util.Set;

public abstract class BrooklynJerseyTest extends JerseyTest {

  protected abstract Set<Object> getResources();

  @Override
  protected AppDescriptor configure() {
    final DropwizardResourceConfig config = new DropwizardResourceConfig();
    for (Object provider : JavaBundle.DEFAULT_PROVIDERS) {
      config.getSingletons().add(provider);
    }

    Json json = new Json();
    config.getSingletons().add(new JacksonMessageBodyProvider(json));
    config.getSingletons().addAll(getResources());

    return new LowLevelAppDescriptor.Builder(config).build();
  }

  @Override
  public URI getBaseURI() {
    return super.getBaseURI();
  }
}
