package brooklyn.rest.domain;

import java.net.URI;
import java.util.Map;
import java.util.Set;


import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.google.common.collect.ImmutableMap;

public class EffectorSummary {

  public static class ParameterSummary {
    private final String name;
    private final String type;
    @JsonSerialize(include=Inclusion.NON_NULL)
    private final String description;

    public ParameterSummary(
        @JsonProperty("name") String name,
        @JsonProperty("type") String type,
        @JsonProperty("description") String description
    ) {
      this.name = name;
      this.type = type;
      this.description = description;
    }

    public String getName() {
      return name;
    }

    public String getType() {
      return type;
    }

    public String getDescription() {
      return description;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ParameterSummary that = (ParameterSummary) o;

      if (description != null ? !description.equals(that.description) : that.description != null)
        return false;
      if (name != null ? !name.equals(that.name) : that.name != null)
        return false;
      if (type != null ? !type.equals(that.type) : that.type != null)
        return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = name != null ? name.hashCode() : 0;
      result = 31 * result + (type != null ? type.hashCode() : 0);
      result = 31 * result + (description != null ? description.hashCode() : 0);
      return result;
    }

    @Override
    public String toString() {
      return "ParameterSummary{" +
          "name='" + name + '\'' +
          ", type='" + type + '\'' +
          ", description='" + description + '\'' +
          '}';
    }
  }

  private final String name;
  private final String returnType;
  private final Set<ParameterSummary> parameters;
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final String description;
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final Map<String, URI> links;

  public EffectorSummary(
      @JsonProperty("name") String name,
      @JsonProperty("returnType") String returnType,
      @JsonProperty("parameters") Set<ParameterSummary> parameters,
      @JsonProperty("description") String description,
      @JsonProperty("links") Map<String, URI> links
  ) {
    this.name = name;
    this.description = description;
    this.returnType = returnType;
    this.parameters = parameters;
    this.links = links != null ? ImmutableMap.copyOf(links) : null;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getReturnType() {
    return returnType;
  }

  public Set<ParameterSummary> getParameters() {
    return parameters;
  }

  public Map<String, URI> getLinks() {
    return links;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    EffectorSummary that = (EffectorSummary) o;

    if (description != null ? !description.equals(that.description) : that.description != null)
      return false;
    if (links != null ? !links.equals(that.links) : that.links != null)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;
    if (parameters != null ? !parameters.equals(that.parameters) : that.parameters != null)
      return false;
    if (returnType != null ? !returnType.equals(that.returnType) : that.returnType != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + (returnType != null ? returnType.hashCode() : 0);
    result = 31 * result + (parameters != null ? parameters.hashCode() : 0);
    result = 31 * result + (links != null ? links.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "EffectorSummary{" +
        "name='" + name + '\'' +
        ", description='" + description + '\'' +
        ", returnType='" + returnType + '\'' +
        ", parameters=" + parameters +
        ", links=" + links +
        '}';
  }
}
