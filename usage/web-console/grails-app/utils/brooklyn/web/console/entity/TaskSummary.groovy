package brooklyn.web.console.entity;


import java.text.DateFormat
import java.text.SimpleDateFormat

import brooklyn.entity.Entity
import brooklyn.management.Task
import brooklyn.management.internal.AbstractManagementContext;

/** Summary of a Brooklyn Task   */
public class TaskSummary {

    // TODO Shoud the times be left as long, and then be converted on the client-side 
    // (rather than putting display/format logic here)?
    
    final String entityId;
    final String entityDisplayName;
    final String displayName;
    final String description;
    final String id;
    final Collection<String> tags;
    final long rawSubmitTimeUtc;
    final String submitTimeUtc;
    final String startTimeUtc;
    final String endTimeUtc;
    final String currentStatus;
    final String detailedStatus;
    final boolean isEffector;

    // formatter is not thread-safe; use thread-local storage
    private static final ThreadLocal<DateFormat> formatter = new ThreadLocal<DateFormat>() {
        @Override protected DateFormat initialValue() {
            SimpleDateFormat result = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            result.setTimeZone(TimeZone.getTimeZone("GMT"))
            return result
        }
    };

    public TaskSummary(Task task) {
        Entity entity = task.tags.find { it instanceof Entity }
        this.entityId = entity?.id ?: ""
        this.entityDisplayName = entity?.displayName ?: ""
        
        this.displayName = task.displayName
        this.description = task.description
        this.id = task.id
        this.rawSubmitTimeUtc = task.submitTimeUtc
        this.submitTimeUtc = (task.submitTimeUtc == -1) ? "" : formatter.get().format(new Date(task.submitTimeUtc))
        this.startTimeUtc = (task.startTimeUtc == -1) ? "" : formatter.get().format(new Date(task.startTimeUtc))
        this.endTimeUtc = (task.endTimeUtc == -1) ? "" : formatter.get().format(new Date(task.endTimeUtc))
        this.currentStatus = task.statusSummary
        this.detailedStatus = task.getStatusDetail(true)
        
        this.tags = task.tags.collect { ""+it };
        this.isEffector = task.tags?.contains(AbstractManagementContext.EFFECTOR_TAG);
    }

    public String toString() {
        return "Task($id, $displayName, $description @ $submitTimeUtc)"
    }
}
