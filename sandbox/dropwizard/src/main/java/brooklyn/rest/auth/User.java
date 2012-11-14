package brooklyn.rest.auth;

import java.util.Set;
import org.codehaus.jackson.annotate.JsonProperty;

public class User {

  private final String name;

  private final String clearTextPassword;

  private final Set<String> roles;

  public User(
      @JsonProperty("name") String name,
      @JsonProperty("password") String clearTextPassword,
      @JsonProperty("roles") Set<String> roles
  ) {
    this.name = name;
    this.clearTextPassword = clearTextPassword;
    this.roles = roles;
  }

  public String getName() {
    return name;
  }

  public String getClearTextPassword() {
    return clearTextPassword;
  }

  public Set<String> getRoles() {
    return roles;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    User user = (User) o;

    if (clearTextPassword != null ? !clearTextPassword.equals(user.clearTextPassword) : user.clearTextPassword != null)
      return false;
    if (name != null ? !name.equals(user.name) : user.name != null) return false;
    if (roles != null ? !roles.equals(user.roles) : user.roles != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (clearTextPassword != null ? clearTextPassword.hashCode() : 0);
    result = 31 * result + (roles != null ? roles.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "User{" +
        "name='" + name + '\'' +
        ", roles=" + roles +
        '}';
  }
}
