package brooklyn.rest.transform;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.management.HasTaskChildren;
import brooklyn.management.Task;
import brooklyn.rest.domain.LinkWithMetadata;
import brooklyn.rest.domain.TaskSummary;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.BasicTask;

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
      try {
        Preconditions.checkNotNull(task);
        // 'ported' from groovy web console TaskSummary.groovy , not sure if always works as intended
        Entity entity = (Entity) Iterables.tryFind(task.getTags(), Predicates.instanceOf(Entity.class)).orNull();
        String entityId;
        String entityDisplayName;
        URI entityLink;
        
        String selfLink = asLink(task).getLink();

        if (entity != null) {
            entityId = entity.getId();
            entityDisplayName = entity.getDisplayName();
            entityLink = new URI("/v1/applications/"+entity.getApplicationId()+"/"+"entities"+"/"+entity.getId());
        } else {
            entityId = null;
            entityDisplayName = null;
            entityLink = null;
        }

        List<LinkWithMetadata> children = Collections.emptyList();
        if (task instanceof HasTaskChildren) {
            children = new ArrayList<LinkWithMetadata>();
            for (Object t: ((HasTaskChildren)task).getChildrenTasks()) {
                children.add(asLink((Task)t));
            }
        }
        
        Map<String,URI> links = MutableMap.of("self", new URI(selfLink),
                "children", new URI(selfLink+"/"+"children"));
        if (entityLink!=null) links.put("entity", entityLink);
        
        return new TaskSummary(task.getId(), task.getDisplayName(), task.getDescription(), entityId, entityDisplayName, 
                task.getTags(), ifPositive(task.getSubmitTimeUtc()), ifPositive(task.getStartTimeUtc()), ifPositive(task.getEndTimeUtc()),
                task.getStatusSummary(), children, asLink(task.getSubmittedByTask()), 
                task instanceof BasicTask ? asLink(((BasicTask<?>)task).getBlockingTask()) : null, 
                task instanceof BasicTask ? ((BasicTask<?>)task).getBlockingDetails() : null, 
                task.getStatusDetail(true),
                links);
      } catch (URISyntaxException e) {
          // shouldn't happen
          throw Exceptions.propagate(e);
      }
    }

    public static Long ifPositive(Long time) {
        if (time==null || time<=0) return null;
        return time;
    }

    public static LinkWithMetadata asLink(Task t) {
        if (t==null) return null;
        MutableMap<String,Object> data = new MutableMap<String,Object>();
        data.put("id", t.getId());
        data.put("taskName", t.getDisplayName());
        Entity entity = (Entity) Iterables.tryFind(t.getTags(), Predicates.instanceOf(Entity.class)).orNull();
        if (entity!=null) data.put("entityDisplayName", entity.getDisplayName());
        return new LinkWithMetadata("/v1/activities/"+t.getId(), data);
    }

}
