package brooklyn.web.console.entity;


import java.text.DateFormat
import java.text.SimpleDateFormat

import brooklyn.entity.Entity
import brooklyn.management.Task

/** Summary of a Brooklyn Task   */
public class TaskSummary {

    final String entityId;
    final String entityDisplayName;
    final String displayName;
    final String description;
    final String id;
    final Set<String> tags;
    final long rawSubmitTimeUtc;
    final String submitTimeUtc;
    final String startTimeUtc;
    final String endTimeUtc;
    final String currentStatus;
    final String detailedStatus;

    // formatter is not thread-safe; use thread-local storage
    private static final ThreadLocal<DateFormat> formatter = new ThreadLocal<DateFormat>() {
        @Override protected DateFormat initialValue() {
            SimpleDateFormat result = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            result.setTimeZone(TimeZone.getTimeZone("GMT"))
            return result
        }
    };

    public TaskSummary(Task task) {
        this.displayName = task.displayName
        this.description = task.description
        this.id = task.id
        this.rawSubmitTimeUtc = task.submitTimeUtc
        this.submitTimeUtc = (task.submitTimeUtc == -1) ? "" : formatter.get().format(new Date(task.submitTimeUtc))
        this.startTimeUtc = (task.startTimeUtc == -1) ? "" : formatter.get().format(new Date(task.startTimeUtc))
        this.endTimeUtc = (task.endTimeUtc == -1) ? "" : formatter.get().format(new Date(task.endTimeUtc))
        this.currentStatus = task.statusSummary
        this.detailedStatus = task.getStatusDetail(true)
    }

    public String toString() {
        return "Task($id, $displayName, $description @ $submitTimeUtc)"
    }
}
