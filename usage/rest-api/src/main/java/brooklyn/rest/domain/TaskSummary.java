package brooklyn.rest.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

import brooklyn.rest.util.JsonUtils;

import com.google.common.collect.ImmutableList;

public class TaskSummary {

  private final String id;
  private final String entityId;
  private final String entityDisplayName;
  
  private final String displayName;
  private final String description;
  private final Collection<Object> tags;
  private final long rawSubmitTimeUtc;
  private final String submitTimeUtc;
  private final String startTimeUtc;
  private final String endTimeUtc;
  private final String currentStatus;
  private final List<LinkAndText> children;
  private final LinkAndText submittedByTask;
  private final LinkAndText blockingTask;
  private final String blockingDetails;
  private final String detailedStatus;

  
  public TaskSummary(
          @JsonProperty("id") String id, 
          @JsonProperty("entityId") String entityId, 
          @JsonProperty("entityDisplayName") String entityDisplayName, 
          @JsonProperty("displayName") String displayName, 
          @JsonProperty("description") String description, 
          @JsonProperty("tags") Set<Object> tags,
          @JsonProperty("rawSubmitTimeUtc") long rawSubmitTimeUtc, 
          @JsonProperty("submitTimeUtc") String submitTimeUtc, 
          @JsonProperty("startTimeUtc") String startTimeUtc, 
          @JsonProperty("endTimeUtc") String endTimeUtc, 
          @JsonProperty("currentStatus") String currentStatus, 
          @JsonProperty("children") List<LinkAndText> children,
          @JsonProperty("submittedByTask") LinkAndText submittedByTask,
          @JsonProperty("blockingTask") LinkAndText blockingTask,
          @JsonProperty("blockingDetails") String blockingDetails,
          @JsonProperty("detailedStatus") String detailedStatus) {
    this.id = id;
    this.entityId = entityId;
    this.entityDisplayName = entityDisplayName;
    this.displayName = displayName;
    this.description = description;
    this.tags = ImmutableList.<Object>copyOf(tags);
    this.rawSubmitTimeUtc = rawSubmitTimeUtc;
    this.submitTimeUtc = submitTimeUtc;
    this.startTimeUtc = startTimeUtc;
    this.endTimeUtc = endTimeUtc;
    this.currentStatus = currentStatus;
    this.children = children;
    this.blockingDetails = blockingDetails;
    this.blockingTask = blockingTask;
    this.submittedByTask = submittedByTask;
    this.detailedStatus = detailedStatus;
}


  public String getId() {
      return id;
  }

  public String getEntityId() {
    return entityId;
  }

  public String getEntityDisplayName() {
    return entityDisplayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getDescription() {
    return description;
  }

  public Collection<Object> getTags() {
    List<Object> result = new ArrayList<Object>();
    for (Object t: tags)
        result.add(JsonUtils.toJsonable(t));
    return result;
  }

  @JsonIgnore
  public Collection<Object> getRawTags() {
      return tags;
    }

  public long getRawSubmitTimeUtc() {
    return rawSubmitTimeUtc;
  }

  public String getSubmitTimeUtc() {
    return submitTimeUtc;
  }

  public String getStartTimeUtc() {
    return startTimeUtc;
  }

  public String getEndTimeUtc() {
    return endTimeUtc;
  }

  public String getCurrentStatus() {
    return currentStatus;
  }

  public List<LinkAndText> getChildren() {
      return children;
  }

  public LinkAndText getSubmittedByTask() {
      return submittedByTask;
  }
  
  public LinkAndText getBlockingTask() {
    return blockingTask;
  }
  
  public String getBlockingDetails() {
    return blockingDetails;
  }

  public String getDetailedStatus() {
      return detailedStatus;
  }

  @Override
  public String toString() {
    return "TaskSummary{" +
        "id='" + id + '\'' +
        ", displayName='" + displayName + '\'' +
        ", currentStatus='" + currentStatus + '\'' +
        ", startTimeUtc='" + startTimeUtc + '\'' +
        ", endTimeUtc='" + endTimeUtc + '\'' +
        '}';
  }
}
