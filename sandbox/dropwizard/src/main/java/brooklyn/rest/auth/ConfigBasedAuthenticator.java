package brooklyn.rest.auth;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.yammer.dropwizard.auth.AuthenticationException;
import com.yammer.dropwizard.auth.Authenticator;
import com.yammer.dropwizard.auth.basic.BasicCredentials;
import java.util.Map;
import java.util.Set;

/**
 * An authenticator that's using a list of accounts defined
 * in the application configuration file
 */
public class ConfigBasedAuthenticator implements Authenticator<BasicCredentials, User> {

  private final Map<String, User> userByNameIndex;

  public ConfigBasedAuthenticator(Set<User> users) {
    ImmutableMap.Builder<String, User> builder = ImmutableMap.builder();
    for (User user : users) {
      builder.put(user.getName(), user);
    }
    userByNameIndex = builder.build();
  }

  @Override
  public Optional<User> authenticate(BasicCredentials credentials)
      throws AuthenticationException {
    User user = userByNameIndex.get(credentials.getUsername());
    if (user == null || !user.getClearTextPassword().equals(credentials.getPassword())) {
      return Optional.absent();
    }
    return Optional.of(user);
  }
}
