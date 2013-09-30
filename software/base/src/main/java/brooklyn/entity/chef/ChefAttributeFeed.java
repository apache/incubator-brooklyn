package brooklyn.entity.chef;

import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.AbstractFeed;
import brooklyn.event.feed.PollHandler;
import brooklyn.event.feed.Poller;
import brooklyn.event.feed.ssh.SshPollValue;
import brooklyn.management.ExecutionContext;
import brooklyn.util.collections.MutableList;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.time.Duration;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * A sensor feed that retrieves attributes from Chef server and converts selected attributes to sensors.
 *
 * <p>To use this feed, you must provide the entity, the name of the node as it is known to Chef, and a collection of attribute
 * sensors. The attribute sensors must follow the naming convention of starting with the string <tt>chef.attribute.</tt>
 * followed by a period-separated path through the Chef attribute hierarchy. For example, an attribute sensor named
 * <tt>chef.attribute.sql_server.instance_name</tt> would cause the feed to search for a Chef attribute called
 * <tt>sql_server</tt>, and within that an attribute <tt>instance_name</tt>, and set the sensor to the value of this
 * attribute.</p>
 *
 * <p>This feed uses the <tt>knife</tt> tool to query all the attributes on a named node. It then iterates over the configured
 * list of attribute sensors, using the sensor name to locate an equivalent Chef attribute. The sensor is then set to the value
 * of the Chef attribute.</p>
 *
 * <p>Example:</p>
 *
 * {@code
 * @Override
 * protected void connectSensors() {
 *     nodeAttributesFeed = ChefAttributeFeed.newFeed(this, nodeName, new AttributeSensor[]{
 *             SqlServerNode.CHEF_ATTRIBUTE_NODE_NAME,
 *             SqlServerNode.CHEF_ATTRIBUTE_SQL_SERVER_INSTANCE_NAME,
 *             SqlServerNode.CHEF_ATTRIBUTE_SQL_SERVER_PORT,
 *             SqlServerNode.CHEF_ATTRIBUTE_SQL_SERVER_SA_PASSWORD
 *     });
 * }
 * }
 *
 * @since 0.6.0
 * @author richardcloudsoft
 */
public class ChefAttributeFeed extends AbstractFeed {

    /**
     * Prefix for attribute sensor names.
     */
    public static final String CHEF_ATTRIBUTE_PREFIX = "chef.attribute.";

    private static final Logger log = LoggerFactory.getLogger(ChefAttributeFeed.class);

    private final EntityLocal entity;
    private final String nodeName;
    private final AttributeSensor[] chefAttributeSensors;
    private final KnifeTaskFactory<String> knifeTaskFactory;

    /**
     * Create and activate a new Chef attribute feed.
     *
     * @param entity The entity where sensors are to be set.
     * @param nodeName The name of the node known to Chef.
     * @param chefAttributeSensors A collection of attribute sensors that are to be used.
     * @return a reference to a new, activated, ChefAttributeFeed.
     */
    public static ChefAttributeFeed newFeed(EntityLocal entity, String nodeName, AttributeSensor[] chefAttributeSensors) {
        ChefAttributeFeed feed = new ChefAttributeFeed(entity, nodeName, chefAttributeSensors);
        feed.start();
        return feed;
    }

    private ChefAttributeFeed(EntityLocal entity, String nodeName, AttributeSensor[] chefAttributeSensors) {
        super(entity);
        this.entity = entity;
        this.nodeName = nodeName;
        this.chefAttributeSensors = chefAttributeSensors;
        this.knifeTaskFactory = new KnifeNodeAttributeQueryTaskFactory(nodeName);
    }

    @Override
    protected void preStart() {
        final Callable<SshPollValue> getAttributesFromKnife = new Callable<SshPollValue>() {
            public SshPollValue call() throws Exception {
                ProcessTaskWrapper<String> taskWrapper = knifeTaskFactory.newTask();
                final ExecutionContext executionContext = ((EntityInternal) entity).getManagementSupport().getExecutionContext();
                log.debug("START: Running knife to query attributes of Chef node {}", nodeName);
                executionContext.submit(taskWrapper);
                taskWrapper.block();
                log.debug("DONE:  Running knife to query attributes of Chef node {}", nodeName);
                return new SshPollValue(null, taskWrapper.getExitCode(), taskWrapper.getStdout(), taskWrapper.getStderr());
            }
        };

        ((Poller<SshPollValue>) poller).scheduleAtFixedRate(
                new CallInEntityExecutionContext<SshPollValue>(entity, getAttributesFromKnife),
                new SendChefAttributesToSensors(entity, chefAttributeSensors),
                Duration.THIRTY_SECONDS.toMilliseconds());
    }

    /**
     * An implementation of {@link KnifeTaskFactory} that queries for the attributes of a node.
     */
    private static class KnifeNodeAttributeQueryTaskFactory extends KnifeTaskFactory<String> {
        private final String nodeName;

        public KnifeNodeAttributeQueryTaskFactory(String nodeName) {
            super("retrieve attributes of node " + nodeName);
            this.nodeName = nodeName;
        }

        @Override
        protected List<String> initialKnifeParameters() {
            MutableList<String> result = new MutableList<String>();

            result.add("node");
            result.add("show");
            result.add("-l");
            result.add(nodeName);
            result.add("--format");
            result.add("json");

            return result;
        }
    }

    /**
     * A {@link Callable} that wraps another {@link Callable}, where the inner {@link Callable} is executed in the context of a
     * specific entity.
     *
     * @param <T> The type of the {@link Callable}.
     */
    private static class CallInEntityExecutionContext<T> implements Callable<T> {

        private final Callable<T> job;
        private EntityLocal entity;

        private CallInEntityExecutionContext(EntityLocal entity, Callable<T> job) {
            this.job = job;
            this.entity = entity;
        }

        @Override
        public T call() throws Exception {
            final ExecutionContext executionContext = ((EntityInternal) entity).getManagementSupport().getExecutionContext();
            return executionContext.submit(Maps.newHashMap(), job).get();
        }
    }

    /**
     * A poll handler that takes the result of the <tt>knife</tt> invocation and sets the appropriate sensors.
     */
    private static class SendChefAttributesToSensors implements PollHandler<SshPollValue> {
        private AttributeSensor[] chefAttributeSensors;
        private EntityLocal entity;

        public SendChefAttributesToSensors(EntityLocal entity, AttributeSensor[] chefAttributeSensors) {
            this.chefAttributeSensors = chefAttributeSensors;
            this.entity = entity;
        }

        @Override
        public boolean checkSuccess(SshPollValue val) {
            if (val.getExitStatus() != 0) return false;
            String stderr = val.getStderr();
            if (stderr == null || stderr.length() != 0) return false;
            String out = val.getStdout();
            if (out == null || out.length() == 0) return false;
            if (!out.contains("{")) return false;
            return true;
        }

        @Override
        public void onSuccess(SshPollValue val) {
            String stdout = val.getStdout();
            int jsonStarts = stdout.indexOf('{');
            if (jsonStarts > 0)
                stdout = stdout.substring(jsonStarts);
            JsonElement jsonElement = new Gson().fromJson(stdout, JsonElement.class);
            final Iterable<String> prefixes = Lists.newArrayList("", "automatic", "force_override", "override", "normal", "force_default", "default");

            for (AttributeSensor attribute : chefAttributeSensors) {
                if (!attribute.getName().startsWith(CHEF_ATTRIBUTE_PREFIX))
                    continue;

                log.debug("Finding value for attribute sensor "+attribute.getName());
                Iterable<String> path = Lists.newArrayList(attribute.getName().substring(CHEF_ATTRIBUTE_PREFIX.length()).split("\\."));
                JsonElement elementForSensor = null;
                for(String prefix : prefixes) {
                    Iterable<String> prefixedPath = !Strings.isNullOrEmpty(prefix)
                            ? Iterables.concat(ImmutableList.of(prefix), path)
                            : path;
                    elementForSensor = getElementByPath(jsonElement.getAsJsonObject(), prefixedPath);
                    if (elementForSensor != null) {
                        log.debug("Found a value with attribute "+ Joiner.on('.').join(prefixedPath));
                        break;
                    }
                }
                log.debug("Attribute sensor {} value is {}", new Object[]{attribute.getName(), elementForSensor});
                entity.setAttribute(attribute, elementForSensor != null ? elementForSensor.getAsString() : null);
            }
        }

        private JsonElement getElementByPath(JsonElement element, Iterable<String> path) {
            if (Iterables.isEmpty(path)) {
                return element;
            } else {
                String head = Iterables.getFirst(path, null);
                Preconditions.checkArgument(!Strings.isNullOrEmpty(head), "path must not contain empty or null elements");
                Iterable<String> tail = Iterables.skip(path, 1);
                JsonElement child = ((JsonObject) element).get(head);
                return child != null
                        ? getElementByPath(child, tail)
                        : null;
            }
        }

        @Override
        public void onFailure(SshPollValue val) {
            log.error("Chef attribute query did not respond as expected. exitcode={} stdout={} stderr={}", new Object[]{val.getExitStatus(), val.getStdout(), val.getStderr()});
            for (AttributeSensor attribute : chefAttributeSensors) {
                if (!attribute.getName().startsWith(CHEF_ATTRIBUTE_PREFIX))
                    continue;
                entity.setAttribute(attribute, null);
            }
        }

        @Override
        public void onError(Exception error) {
            log.error("Detected error while retrieving Chef attributes", error);
            for (AttributeSensor attribute : chefAttributeSensors) {
                if (!attribute.getName().startsWith(CHEF_ATTRIBUTE_PREFIX))
                    continue;
                entity.setAttribute(attribute, null);
            }
        }

        @Override
        public void onException(Exception exception) {
            log.error("Detected exception while retrieving Chef attributes", exception);
            for (AttributeSensor attribute : chefAttributeSensors) {
                if (!attribute.getName().startsWith(CHEF_ATTRIBUTE_PREFIX))
                    continue;
                entity.setAttribute(attribute, null);
            }
        }
    }
}
