package brooklyn.rest.api;

import org.codehaus.jackson.annotate.JsonProperty;

public class Location {

  public static Location localhost() {
    return new Location("localhost", "", "", "localhost");
  }

  private final String provider;
  private final String location;

  private final String identity;
  private final String credential;

  public Location(
      @JsonProperty("provider") String provider,
      @JsonProperty("identity") String identity,
      @JsonProperty("credential") String credential,
      @JsonProperty("location") String location
  ) {
    this.provider = provider;
    this.identity = identity;
    this.credential = credential;
    this.location = location;
  }

  public String getProvider() {
    return provider;
  }

  public String getIdentity() {
    return identity;
  }

  public String getCredential() {
    return credential;
  }

  public String getLocation() {
    return location;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Location location1 = (Location) o;

    if (credential != null ? !credential.equals(location1.credential) : location1.credential != null)
      return false;
    if (identity != null ? !identity.equals(location1.identity) : location1.identity != null)
      return false;
    if (location != null ? !location.equals(location1.location) : location1.location != null)
      return false;
    if (provider != null ? !provider.equals(location1.provider) : location1.provider != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = provider != null ? provider.hashCode() : 0;
    result = 31 * result + (identity != null ? identity.hashCode() : 0);
    result = 31 * result + (credential != null ? credential.hashCode() : 0);
    result = 31 * result + (location != null ? location.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Location{" +
        "provider='" + provider + '\'' +
        ", identity='" + identity + '\'' +
        ", location='" + location + '\'' +
        '}';
  }
}
