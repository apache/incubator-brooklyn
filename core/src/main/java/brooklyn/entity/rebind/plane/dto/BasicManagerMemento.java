package brooklyn.entity.rebind.plane.dto;

import java.io.Serializable;
import java.net.URI;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;

import brooklyn.BrooklynVersion;
import brooklyn.management.ha.ManagerMemento;
import brooklyn.util.time.Time;

import com.google.common.base.Objects;

/**
 * Represents the state of a management node within the Brooklyn management plane.
 * 
 * @author aled
 */
@JsonAutoDetect(fieldVisibility=Visibility.ANY, getterVisibility=Visibility.NONE)
public class BasicManagerMemento implements ManagerMemento, Serializable {

    private static final long serialVersionUID = 4918161834047884244L;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String brooklynVersion = BrooklynVersion.get();
        protected String nodeId;
        protected URI uri;
        protected HealthStatus status;
        protected long timestampUtc;

        protected Builder self() {
            return (Builder) this;
        }
        public Builder brooklynVersion(String val) {
            brooklynVersion = val; return self();
        }
        public Builder nodeId(String val) {
            nodeId = val; return self();
        }
        public Builder uri(URI val) {
            uri = val; return self();
        }
        public Builder status(HealthStatus val) {
            status = val; return self();
        }
        public Builder timestampUtc(long val) {
            timestampUtc = val; return self();
        }
        public Builder from(BasicManagerMemento other) {
            nodeId = other.getNodeId();
            brooklynVersion = other.getBrooklynVersion();
            timestampUtc = other.getTimestampUtc();
            uri = other.getUri();
            status = other.getStatus();
            return this;
        }
        public ManagerMemento build() {
            return new BasicManagerMemento(this);
        }
    }
    
    private String brooklynVersion;
    private String nodeId;
    private URI uri;
    private HealthStatus status;
    private long timestampUtc;

    // for de-serialization
    @SuppressWarnings("unused")
    private BasicManagerMemento() {
    }

    // Trusts the builder to not mess around with mutability concurrently with build().
    protected BasicManagerMemento(Builder builder) {
        brooklynVersion = builder.brooklynVersion;
        nodeId = builder.nodeId;
        uri = builder.uri;
        status = builder.status;
        timestampUtc = builder.timestampUtc;
    }

    @Override
    public String getBrooklynVersion() {
        return brooklynVersion;
    }
    
    @Override
    public String getNodeId() {
        return nodeId;
    }
    
    @Override
    public URI getUri() {
        return uri;
    }
    
    @Override
    public HealthStatus getStatus() {
        return status;
    }
    
    @Override
    public long getTimestampUtc() {
        return timestampUtc;
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("nodeId", getNodeId())
                .add("status", getStatus()).toString();
    }
    
    @Override
    public String toVerboseString() {
        return Objects.toStringHelper(this)
                .omitNullValues()
                .add("brooklynVersion", getBrooklynVersion())
                .add("nodeId", getNodeId())
                .add("uri", getUri())
                .add("status", getStatus())
                .add("timestamp", getTimestampUtc()+"="+Time.makeDateString(getTimestampUtc()))
                .toString();
    }
}
