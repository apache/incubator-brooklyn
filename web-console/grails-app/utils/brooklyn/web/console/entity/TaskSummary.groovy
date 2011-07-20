package brooklyn.web.console.entity;


import brooklyn.management.Task
import java.text.DateFormat
import java.text.SimpleDateFormat

/** Summary of a Brookln Task   */
public class TaskSummary {

    final String displayName;
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
    final String currentStatus;

    DateFormat formatter = new SimpleDateFormat("hh:mm:ss dd/MM/yyyy ")
    Calendar date = Calendar.getInstance();

    public TaskSummary(Task task) {
        this.displayName = task.displayName
        this.description = task.description

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
        if(submitted){
            currentStatus = "Submitted"
        }

        this.done = task.done
        if(done){
            currentStatus = "Done"
        }

        this.error = task.error
        if(error){
            currentStatus = "Error"
        }

        this.cancelled = task.cancelled
        if(cancelled){
            currentStatus = "Cancelled"
        }
    }

}
