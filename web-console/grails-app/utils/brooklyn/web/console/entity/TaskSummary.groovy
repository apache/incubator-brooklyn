package brooklyn.web.console.entity;


import brooklyn.management.Task
import java.text.DateFormat
import java.text.SimpleDateFormat

/** Summary of a Brooklyn Task   */
public class TaskSummary {

    final String displayName;
    final String description;
    final String id;
    final Set<String> tags;
    final String submitTimeUtc;
    final String startTimeUtc;
    final String endTimeUtc;
    final String currentStatus;
    final String detailedStatus;

    private DateFormat formatter = new SimpleDateFormat("hh:mm:ss dd/MM/yyyy")

    public TaskSummary(Task task) {
        this.displayName = task.displayName
        this.description = task.description
        this.id = task.id
        this.submitTimeUtc = formatter.format(new Date(task.submitTimeUtc))
        this.startTimeUtc = formatter.format(new Date(task.startTimeUtc))
        this.endTimeUtc = formatter.format(new Date(task.endTimeUtc))
        this.currentStatus = task.statusSummary
        this.detailedStatus = task.getStatusDetail(true)
    }

}
