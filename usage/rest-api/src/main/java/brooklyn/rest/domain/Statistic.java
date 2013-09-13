package brooklyn.rest.domain;

import org.codehaus.jackson.annotate.JsonProperty;

import java.util.Map;

/**
 * @author Adam Lowe
 */
public class Statistic {
    @JsonProperty
    private final Status status;
    @JsonProperty
    private final String id;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final String start;
    @JsonProperty
    private final String end;
    @JsonProperty
    private final long duration;
    @JsonProperty
    private final Map<String,String> metadata;

    public Statistic(@JsonProperty("status") Status status, @JsonProperty("id") String id, @JsonProperty("name") String name,
                            @JsonProperty("start") String start, @JsonProperty("end") String end,
                            @JsonProperty("duration") long duration, @JsonProperty("metadata") Map<String, String> metadata) {
        this.status = status;
        this.id = id;
        this.name = name;
        this.start = start;
        this.end = end;
        this.duration = duration;
        this.metadata = metadata;
    }

    public Status getStatus() {
        return status;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
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

        Statistic usage = (Statistic) o;

        if (status != usage.status) return false;
        if (duration != usage.duration) return false;
        if (end != null ? !end.equals(usage.end) : usage.end != null) return false;
        if (!id.equals(usage.id)) return false;
        if (name != null ? !name.equals(usage.name) : usage.name != null) return false;
        if (start != null ? !start.equals(usage.start) : usage.start != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (start != null ? start.hashCode() : 0);
        result = 31 * result + (end != null ? end.hashCode() : 0);
        result = 31 * result + (int) (duration ^ (duration >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "Usage{" +
                ", id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", status='" + status + '\'' +
                ", start=" + start +
                ", end=" + end +
                ", duration=" + duration +
                ", metadata=" + metadata +
                '}';
    }
}
