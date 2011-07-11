package brooklyn.web.console.entity;


import brooklyn.management.Task

/** Summary of a Brookln Task   */
public class TaskSummary {

    final String id;
    final Set<String> tags;
    final long submitTimeUtc;
    final long startTimeUtc;
    final long endTimeUtc;
    final String submittedByTask;
    final String statusDetail;
    final String statusDetailMultiLine;
    final boolean submitted;
    final boolean done;
    final boolean error;
    final boolean cancelled;

    public TaskSummary(Task task) {
        this.id = task.getId()
        this.submitTimeUtc = task.submitTimeUtc
        this.startTimeUtc = task.startTimeUtc
        this.endTimeUtc = task.endTimeUtc
        this.submittedByTask = task.submittedByTask.id
        this.statusDetail = task.getStatusDetail(false)
        this.statusDetailMultiLine = task.getStatusDetail(true)
        this.submittedByTask = task.submittedByTask
        this.submitted = task.submitted
        this.done = task.done
        this.error = task.error
        this.cancelled = task.cancelled
    }

}
