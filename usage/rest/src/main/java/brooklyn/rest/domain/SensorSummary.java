package brooklyn.rest.domain;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.render.RendererHints;
import brooklyn.config.render.RendererHints.Hint;
import brooklyn.config.render.RendererHints.NamedAction;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

public class SensorSummary {

    private static final Logger log = LoggerFactory.getLogger(SensorSummary.class);
    
  private final String name;
  private final String type;
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final String description;
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final Map<String, URI> links;

  public SensorSummary(
      @JsonProperty("name") String name,
      @JsonProperty("type") String type,
      @JsonProperty("description") String description,
      @JsonProperty("links") Map<String, URI> links
  ) {
    this.name = name;
    this.type = type;
    this.description = description;
    this.links = links != null ? ImmutableMap.copyOf(links) : null;
  }

  @Deprecated
  public SensorSummary(ApplicationSummary application, EntityLocal entity, Sensor<?> sensor) {
      this(entity, sensor);
  }
  @SuppressWarnings("rawtypes")
protected SensorSummary(Entity entity, Sensor<?> sensor) {
    this.name = sensor.getName();
    this.type = sensor.getTypeName();
    this.description = sensor.getDescription();

    String applicationUri = "/v1/applications/" + entity.getApplicationId();
    String entityUri = applicationUri + "/entities/" + entity.getId();
    String selfUri = entityUri + "/sensors/" + sensor.getName();
    
    ImmutableMap.Builder<String, URI> lb = ImmutableMap.<String, URI>builder()
        .put("self", URI.create(selfUri))
        .put("application", URI.create(applicationUri))
        .put("entity", URI.create(entityUri))
        .put("action:json", URI.create(selfUri));
    Set<Hint> hints = RendererHints.getHintsFor(sensor);
    for (Hint h: hints) addRendererHint(lb, h, entity, sensor);
    this.links = lb.build();
  }

  @SuppressWarnings("rawtypes")
  private void addRendererHint(Builder<String, URI> lb, Hint h, Entity entity, Sensor<?> sensor) {
      if (!(h instanceof NamedAction))
          return;
      if (h instanceof RendererHints.NamedActionWithUrl) {
          try {
              String v = ((RendererHints.NamedActionWithUrl)h).getUrl(entity, (AttributeSensor<?>) sensor);
              if (v!=null && !v.isEmpty()) lb.put("action:open", URI.create(v));
          } catch (Exception e) {
              Exceptions.propagateIfFatal(e);
              log.warn("Unable to make use of URL sensor "+sensor+" on "+entity+": "+e);
          }
      }
  }

  public static SensorSummary fromEntity(EntityLocal entity, Sensor<?> sensor) {
      return new SensorSummary(entity, sensor);
  }

  public static SensorSummary forCatalog(Sensor<?> sensor) {
      return new SensorSummary(sensor.getName(), sensor.getTypeName(),
              sensor.getDescription(), null);
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

  public Map<String, URI> getLinks() {
    return links;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SensorSummary that = (SensorSummary) o;

    if (description != null ? !description.equals(that.description) : that.description != null)
      return false;
    if (links != null ? !links.equals(that.links) : that.links != null)
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
    result = 31 * result + (links != null ? links.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "SensorSummary{" +
        "name='" + name + '\'' +
        ", type='" + type + '\'' +
        ", description='" + description + '\'' +
        ", links=" + links +
        '}';
  }

}
