package brooklyn.web.console.entity;


import brooklyn.management.Task
import java.text.DateFormat
import java.text.SimpleDateFormat

/** Summary of a Brookln Task   */
public class TaskSummary {

    final String name;
    final String description;
    final String id;
    final Set<String> tags;
    final String submitTimeUtc;
    final String startTimeUtc;
    final String endTimeUtc;
    final String submittedByTask;
    final String statusDetail;
    final String statusDetailMultiLine;
    final boolean submitted;
    final boolean done;
    final boolean error;
    final boolean cancelled;

    DateFormat formatter = new SimpleDateFormat("hh:mm:ss dd/MM/yyyy ")
    Calendar date = Calendar.getInstance();

    public TaskSummary(Task task) {
        //TODO Add name and description to task interface
        this.name = "Activity"
        this.description = "This is some activity"

        this.id = task.id

        date.setTimeInMillis(task.submitTimeUtc)
        this.submitTimeUtc = formatter.format(date.getTime())
        date.setTimeInMillis(task.startTimeUtc)
        this.startTimeUtc = formatter.format(date.getTime())
        date.setTimeInMillis(task.endTimeUtc)
        this.endTimeUtc = formatter.format(date.getTime())

        this.submittedByTask = task.submittedByTask ? task.submittedByTask.id : null
        this.statusDetail = task.getStatusDetail(false)
        this.statusDetailMultiLine = task.getStatusDetail(true)
        this.submittedByTask = task.submittedByTask
        this.submitted = task.submitted
        this.done = task.done
        this.error = task.error
        this.cancelled = task.cancelled
    }

}
