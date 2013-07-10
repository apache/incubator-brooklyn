package brooklyn.rest.transform;

import brooklyn.entity.Entity;
import brooklyn.management.Task;
import brooklyn.rest.domain.TaskSummary;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class TaskTransformer {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(TaskTransformer.class);

    public static final Function<Task<?>, TaskSummary> FROM_TASK = new Function<Task<?>, TaskSummary>() {
        @Override
        public TaskSummary apply(@Nullable Task<?> input) {
            return taskSummary(input);
        }
    };

    public static TaskSummary taskSummary(Task task) {
        Preconditions.checkNotNull(task);
        // 'ported' from groovy web console TaskSummary.groovy , not sure if always works as intended
        Entity entity = (Entity) Iterables.tryFind(task.getTags(), Predicates.instanceOf(Entity.class)).orNull();
        String entityId;
        String entityDisplayName;

        if (entity != null) {
            entityId = entity.getId();
            entityDisplayName = entity.getDisplayName();
        } else {
            entityId = null;
            entityDisplayName = null;
        }

        String submitTimeUtc = (task.getSubmitTimeUtc() == -1) ? "" : formatter.get().format(new Date(task.getSubmitTimeUtc()));
        String startTimeUtc = (task.getStartTimeUtc() == -1) ? "" : formatter.get().format(new Date(task.getStartTimeUtc()));
        String endTimeUtc = (task.getEndTimeUtc() == -1) ? "" : formatter.get().format(new Date(task.getEndTimeUtc()));

        return new TaskSummary(entityId, entityDisplayName, task.getDisplayName(), task.getDescription(),
                task.getId(), task.getTags(), task.getSubmitTimeUtc(), submitTimeUtc, startTimeUtc, endTimeUtc,
                task.getStatusSummary(), task.getStatusDetail(true));
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

}
