package brooklyn.util.task;

import java.util.Set;

import javax.annotation.Nullable;

import brooklyn.management.Task;
import brooklyn.management.TaskAdaptable;

import com.google.common.base.Function;

public class TaskTags {

    /** marks a task which is allowed to fail without failing his parent */
    public static final String INESSENTIAL_TASK = "inessential";

    /** marks a task which is a subtask of another */
    public static final String SUB_TASK_TAG = "SUB-TASK";

    public static void addTagDynamically(TaskAdaptable<?> task, final Object tag) {
        ((BasicTask<?>)task.asTask()).applyTagModifier(new Function<Set<Object>, Void>() {
            public Void apply(@Nullable Set<Object> input) {
                input.add(tag);
                return null;
            }
        });
    }
    
    public static void addTagsDynamically(TaskAdaptable<?> task, final Object tag1, final Object ...tags) {
        ((BasicTask<?>)task.asTask()).applyTagModifier(new Function<Set<Object>, Void>() {
            public Void apply(@Nullable Set<Object> input) {
                input.add(tag1);
                for (Object tag: tags) input.add(tag);
                return null;
            }
        });
    }

    
    public static boolean isInessential(Task<?> task) {
        return task.getTags().contains(INESSENTIAL_TASK);
    }
    
    public static void markInessential(Task<?> task) {
        addTagDynamically(task, INESSENTIAL_TASK);
    }

}
