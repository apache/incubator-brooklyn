package brooklyn.rest.transform;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.annotation.Nullable;

import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.management.HasTaskChildren;
import brooklyn.management.Task;
import brooklyn.rest.domain.TaskSummary;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

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

        List<String> children = Collections.emptyList();
        if (task instanceof HasTaskChildren) {
            children = new ArrayList<String>();
            for (Object t: ((HasTaskChildren)task).getChildrenTasks()) {
                children.add(asLink((Task)t));
            }
        }
        return new TaskSummary(task.getId(), entityId, entityDisplayName, task.getDisplayName(), task.getDescription(),
                task.getTags(), task.getSubmitTimeUtc(), submitTimeUtc, startTimeUtc, endTimeUtc,
                task.getStatusSummary(), children, asLink(task.getSubmittedByTask()), task.getStatusDetail(true));
    }

    public static String asLink(Task t) {
        if (t==null) return null;
        return "/v1/activities/"+t.getId();
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
