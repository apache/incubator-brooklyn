package brooklyn.rest.domain;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * @author Aled Sage
 */
public class UsageStatistics {
    
    // TODO populate links with /apps endpoint to link to /usage/applications/{id}, to make it more RESTy
    
    private final List<UsageStatistic> statistics;
    private final Map<String, URI> links;

    public UsageStatistics(
            @JsonProperty("statistics") List<UsageStatistic> statistics,
            @JsonProperty("links") Map<String, URI> links
    ) {
        this.statistics = statistics == null ? ImmutableList.<UsageStatistic>of() : ImmutableList.copyOf(statistics);
        this.links = links == null ? ImmutableMap.<String, URI>of() : ImmutableMap.copyOf(links);
      }

    public List<UsageStatistic> getStatistics() {
        return statistics;
    }

    public Map<String, URI> getLinks() {
        return links;
    }
    
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UsageStatistics)) return false;
        UsageStatistics other = (UsageStatistics) o;
        return Objects.equal(statistics,  other.statistics);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(statistics);
    }

    @Override
    public String toString() {
        return "UsageStatistics{" +
                "statistics=" + statistics +
                ", links=" + links +
                '}';
    }
}
