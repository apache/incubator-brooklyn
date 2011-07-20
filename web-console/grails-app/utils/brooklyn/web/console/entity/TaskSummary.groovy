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
    final String submittedByTask;
    final String statusDetail;
    final String statusDetailMultiLine;
    final boolean submitted;
    final boolean done;
    final boolean error;
    final boolean cancelled;
    final String currentStatus;

    private DateFormat formatter = new SimpleDateFormat("hh:mm:ss dd/MM/yyyy")

    public TaskSummary(Task task) {
        this.displayName = task.displayName
        this.description = task.description

        this.id = task.id

        this.submitTimeUtc = formatter.format(new Date(task.submitTimeUtc))
        this.startTimeUtc = formatter.format(new Date(task.startTimeUtc))
        this.endTimeUtc = formatter.format(new Date(task.endTimeUtc))

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
