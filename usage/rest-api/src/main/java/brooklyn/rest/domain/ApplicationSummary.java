package brooklyn.rest.domain;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

import com.google.common.collect.ImmutableMap;

public class ApplicationSummary {

    private final static Map<Status, Status> validTransitions =
            ImmutableMap.<Status, Status>builder()
                    .put(Status.UNKNOWN, Status.ACCEPTED)
                    .put(Status.ACCEPTED, Status.STARTING)
                    .put(Status.STARTING, Status.RUNNING)
                    .put(Status.RUNNING, Status.STOPPING)
                    .put(Status.STOPPING, Status.STOPPED)
                    .put(Status.STOPPED, Status.STARTING)
                    .build();

    private final ApplicationSpec spec;
    private final Status status;
    private final Map<String, URI> links;

    @JsonIgnore
    private transient Object instanceId;

    public ApplicationSummary(
            @JsonProperty("spec") ApplicationSpec spec,
            @JsonProperty("status") Status status,
            @JsonProperty("links") Map<String, URI> links
    ) {
        this(spec, status, links, null);
    }

    public ApplicationSummary(
            ApplicationSpec spec,
            Status status,
            Map<String, URI> links,
            Object instanceId
    ) {
        this.spec = checkNotNull(spec, "spec");
        this.status = checkNotNull(status, "status");
        this.links = links == null ? ImmutableMap.<String, URI>of() : ImmutableMap.copyOf(links);
        this.instanceId = instanceId;
    }

    public ApplicationSpec getSpec() {
        return spec;
    }

    @JsonIgnore
    public Object getId() {
        return instanceId;
    }

    public Status getStatus() {
        return status;
    }

    public Map<String, URI> getLinks() {
        return links;
    }

    public ApplicationSummary transitionTo(Status newStatus) {
        if (newStatus == Status.ERROR || validTransitions.get(status) == newStatus) {
            return new ApplicationSummary(spec, newStatus, links, instanceId);
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
                ", instance=" + instanceId +
                '}';
    }

}
