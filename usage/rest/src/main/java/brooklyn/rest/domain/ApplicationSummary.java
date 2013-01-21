package brooklyn.rest.domain;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

import brooklyn.entity.Application;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.trait.Startable;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;

public class ApplicationSummary {

  public static enum Status {
    ACCEPTED,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    ERROR,
    UNKNOWN;

    public static Status fromApplication(Application application) {
        if (application==null) return UNKNOWN;
        Lifecycle state = application.getAttribute(Attributes.SERVICE_STATE);
        if (state!=null) return fromLifecycle(state);
        Boolean up = application.getAttribute(Startable.SERVICE_UP);
        if (up!=null && up.booleanValue()) return Status.RUNNING;
        return Status.UNKNOWN;
    }
    public static Status fromLifecycle(Lifecycle state) {
        if (state==null) return UNKNOWN;
        switch (state) {
        case CREATED: return ACCEPTED;
        case STARTING: return STARTING;
        case RUNNING: return RUNNING;
        case STOPPING: return STOPPING;
        case STOPPED: return STOPPED; 
        case DESTROYED: 
        case ON_FIRE:
        default: 
            return UNKNOWN;
        }
    }
  }

  private final static Map<Status, Status> validTransitions =
      ImmutableMap.<Status, Status>builder()
          .put(Status.UNKNOWN, Status.ACCEPTED)
          .put(Status.ACCEPTED, Status.STARTING)
          .put(Status.STARTING, Status.RUNNING)
          .put(Status.RUNNING, Status.STOPPING)
          .put(Status.STOPPING, Status.STOPPED)
          .put(Status.STOPPED, Status.STARTING)
          .build();

  public static final Function<? super Application, ApplicationSummary> FROM_APPLICATION = new Function<Application, ApplicationSummary>() {
      @Override
      public ApplicationSummary apply(Application input) { return fromApplication(input); }
  };

  private final ApplicationSpec spec;
  private final Status status;

  @JsonIgnore
  private transient Application instance;

  public ApplicationSummary(
      @JsonProperty("spec") ApplicationSpec spec,
      @JsonProperty("status") Status status
  ) {
    this.spec = checkNotNull(spec, "spec");
    this.status = checkNotNull(status, "status");
  }

  public ApplicationSummary(ApplicationSpec spec, Status status, Application instance) {
    this.spec = checkNotNull(spec, "spec");
    this.status = checkNotNull(status, "status");
    this.instance = instance;
  }

  public static ApplicationSummary fromApplication(Application application) {
      return new ApplicationSummary(ApplicationSpec.fromApplication(application), 
              Status.fromApplication(application), 
              application);
  }
  
  public ApplicationSpec getSpec() {
    return spec;
  }

  @JsonIgnore
  public Object getId() {
    return instance.getId();
  }

  @JsonIgnore
  public Application getInstance() {
    return instance;
  }

  public Status getStatus() {
    return status;
  }

  /* only to make jackson happy */
  @SuppressWarnings("unused")
  private void setLinks(Map<String, URI> _) {
  }

  public Map<String, URI> getLinks() {
    if (getId()==null) return Collections.emptyMap();
    return ImmutableMap.of(
        "self", URI.create("/v1/applications/" + getId()),
        "entities", URI.create("/v1/applications/" + getId() + "/entities")
    );
  }

  public ApplicationSummary transitionTo(Status newStatus) {
    if (newStatus == Status.ERROR || validTransitions.get(status) == newStatus) {
      return new ApplicationSummary(spec, newStatus, instance);
    }
    throw new IllegalStateException("Invalid transition from '" +
        status + "' to '" + newStatus + "'");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ApplicationSummary that = (ApplicationSummary) o;

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
