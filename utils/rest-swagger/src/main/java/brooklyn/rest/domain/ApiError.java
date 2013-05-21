package brooklyn.rest.domain;

import static com.google.common.base.Preconditions.checkNotNull;
import org.codehaus.jackson.annotate.JsonProperty;

public class ApiError {

  private final String message;

  public ApiError(@JsonProperty("message") String message) {
    this.message = checkNotNull(message, "message");
  }

  public String getMessage() {
    return message;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ApiError apiError = (ApiError) o;

    if (message != null ? !message.equals(apiError.message) : apiError.message != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    return message != null ? message.hashCode() : 0;
  }

  @Override
  public String toString() {
    return "ApiError{" +
        "message='" + message + '\'' +
        '}';
  }
}
