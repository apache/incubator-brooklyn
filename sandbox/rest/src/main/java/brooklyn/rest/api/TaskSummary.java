package brooklyn.rest.api;

import brooklyn.entity.Entity;
import brooklyn.management.Task;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.base.Predicate;
import static com.google.common.collect.Iterators.find;
import org.codehaus.jackson.annotate.JsonIgnore;

import javax.annotation.Nullable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.TimeZone;

public class TaskSummary {

  private final String entityId;
  private final String entityDisplayName;
  private final String displayName;
  private final String description;
  private final String id;
  private final Set<Object> tags;
  private final long rawSubmitTimeUtc;
  private final String submitTimeUtc;
  private final String startTimeUtc;
  private final String endTimeUtc;
  private final String currentStatus;
  private final String detailedStatus;


  public TaskSummary(Task task) {
    checkNotNull(task);
    // 'ported' from groovy web console TaskSummary.groovy , not sure if always works as intended
    Entity entity = (Entity) find(task.getTags().iterator(), new Predicate<Object>() {
      @Override
      public boolean apply(@Nullable Object input) {
        return input instanceof Entity;
      }
    });

    this.entityId = entity.getId();
    this.entityDisplayName = entity.getDisplayName();

    this.tags = task.getTags();
    this.displayName = task.getDisplayName();
    this.description = task.getDescription();
    this.id = task.getId();
    this.rawSubmitTimeUtc = task.getSubmitTimeUtc();
    this.submitTimeUtc = (task.getSubmitTimeUtc() == -1) ? "" : formatter.get().format(new Date(task.getSubmitTimeUtc()));
    this.startTimeUtc = (task.getStartTimeUtc() == -1) ? "" : formatter.get().format(new Date(task.getStartTimeUtc()));
    this.endTimeUtc = (task.getEndTimeUtc() == -1) ? "" : formatter.get().format(new Date(task.getEndTimeUtc()));
    this.currentStatus = task.getStatusSummary();
    this.detailedStatus = task.getStatusDetail(true);
  }

  // formatter is not thread-safe; use thread-local storage
  private static final ThreadLocal<DateFormat> formatter = new ThreadLocal<DateFormat>() {
    @Override
    protected DateFormat initialValue() {
      SimpleDateFormat result = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      result.setTimeZone(TimeZone.getTimeZone("GMT"));
      return result;
    }
  };

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

  public String getId() {
    return id;
  }

  @JsonIgnore
  public Set<Object> getTags() {
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
