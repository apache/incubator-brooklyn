package brooklyn.rest.domain;

import org.codehaus.jackson.annotate.JsonProperty;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Adam Lowe
 */
public class Statistic {
    private final Status status;
    private final String id;
    private final String applicationId;
    private final String start;
    private final String end;
    private final long duration;
    private final Map<String,String> metadata;

    public Statistic(@JsonProperty("status") Status status, @JsonProperty("id") String id, @JsonProperty("applicationId") String applicationId,
                     @JsonProperty("start") String start,
                     @JsonProperty("end") String end,
                     @JsonProperty("duration") long duration, @JsonProperty("metadata") Map<String, String> metadata) {
        this.status = checkNotNull(status, "status");
        this.id = checkNotNull(id, "id");
        this.applicationId = applicationId;
        this.start = start;
        this.end = end;
        this.duration = duration;
        this.metadata = checkNotNull(metadata, "metadata");
    }

    public Status getStatus() {
        return status;
    }

    public String getId() {
        return id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getStart() {
        return start;
    }

    public String getEnd() {
        return end;
    }

    public long getDuration() {
        return duration;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Statistic statistic = (Statistic) o;

        if (applicationId != null ? !applicationId.equals(statistic.applicationId) : statistic.applicationId != null)
            return false;
        if (end != null ? !end.equals(statistic.end) : statistic.end != null) return false;
        if (!id.equals(statistic.id)) return false;
        if (!metadata.equals(statistic.metadata)) return false;
        if (start != null ? !start.equals(statistic.start) : statistic.start != null) return false;
        if (status != statistic.status) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = status.hashCode();
        result = 31 * result + id.hashCode();
        result = 31 * result + (applicationId != null ? applicationId.hashCode() : 0);
        result = 31 * result + (start != null ? start.hashCode() : 0);
        result = 31 * result + (end != null ? end.hashCode() : 0);
        result = 31 * result + metadata.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Statistic{" +
                "status=" + status +
                ", id='" + id + '\'' +
                ", applicationId='" + applicationId + '\'' +
                ", start='" + start + '\'' +
                ", end='" + end + '\'' +
                ", duration=" + duration +
                ", metadata=" + metadata +
                '}';
    }
}
