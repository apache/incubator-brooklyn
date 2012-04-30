package brooklyn.rest.api;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.EntityLocal;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

public class Application {

  public static enum Status {
    ACCEPTED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR,
    UNKNOWN
  }

  private final static Map<Status, Status> validTransitions =
      ImmutableMap.<Status, Status>builder()
          .put(Status.UNKNOWN, Status.ACCEPTED)
          .put(Status.ACCEPTED, Status.STARTING)
          .put(Status.STARTING, Status.RUNNING)
          .put(Status.RUNNING, Status.STOPPING)
          .build();

  private ApplicationSpec spec;
  private Status status = Status.UNKNOWN;

  @JsonIgnore
  private transient AbstractApplication instance;

  public Application(
      @JsonProperty("spec") ApplicationSpec spec,
      @JsonProperty("status") Status status
  ) {
    this.spec = checkNotNull(spec, "spec");
    this.status = checkNotNull(status, "status");
  }

  public Application(ApplicationSpec spec, Status status, AbstractApplication instance) {
    this.spec = checkNotNull(spec, "spec");
    this.status = checkNotNull(status, "status");
    this.instance = instance;
  }

  public ApplicationSpec getSpec() {
    return spec;
  }

  @JsonIgnore
  public AbstractApplication getInstance() {
    return instance;
  }

  public Status getStatus() {
    return status;
  }

  public Application transitionTo(Status newStatus) {
    if (newStatus == Status.ERROR || validTransitions.get(status) == newStatus) {
      return new Application(spec, newStatus, instance);
    }
    throw new IllegalStateException("Invalid transition from '" +
        status + "' to '" + newStatus + "'");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Application that = (Application) o;

    if (spec != null ? !spec.equals(that.spec) : that.spec != null)
      return false;
    if (status != that.status) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = spec != null ? spec.hashCode() : 0;
    result = 31 * result + (status != null ? status.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Application{" +
        "spec=" + spec +
        ", status=" + status +
        ", instance=" + instance +
        '}';
  }
}
