package brooklyn.entity.chef;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.TaskFactory;
import brooklyn.util.task.DynamicTasks;

import com.google.common.annotations.Beta;
import com.google.common.reflect.TypeToken;

/** Driver class to facilitate use of Chef */
@Beta
public class ChefSoloDriver extends AbstractSoftwareProcessSshDriver implements ChefConfig {

    @SuppressWarnings("serial")
    public static final ConfigKey<TaskFactory<? extends TaskAdaptable<Boolean>>> IS_RUNNING_TASK = ConfigKeys.newConfigKey(
            new TypeToken<TaskFactory<? extends TaskAdaptable<Boolean>>>() {}, 
            "brooklyn.chef.task.driver.isRunningTask");
    
    @SuppressWarnings("serial")
    public static final ConfigKey<TaskFactory<?>> STOP_TASK = ConfigKeys.newConfigKey(
            new TypeToken<TaskFactory<?>>() {}, 
            "brooklyn.chef.task.driver.stopTask");
    
    public ChefSoloDriver(EntityLocal entity, SshMachineLocation location) {
        super(entity, location);
    }

    @Override
    public void install() {
        // TODO flag to force reinstallation
        DynamicTasks.queue(
                ChefSoloTasks.installChef(getInstallDir(), false), 
                ChefSoloTasks.installCookbooks(getInstallDir(), getRequiredConfig(CHEF_COOKBOOKS), false));
    }

    @Override
    public void customize() {
        DynamicTasks.queue(ChefSoloTasks.buildChefFile(getRunDir(), getInstallDir(), "launch", getRequiredConfig(CHEF_RUN_LIST),
                getEntity().getConfig(CHEF_LAUNCH_ATTRIBUTES)));
    }

    @Override
    public void launch() {
        DynamicTasks.queue(ChefSoloTasks.runChef(getRunDir(), "launch", getEntity().getConfig(CHEF_RUN_CONVERGE_TWICE)));
    }

    @Override
    public boolean isRunning() {
        return DynamicTasks.queue(getRequiredConfig(IS_RUNNING_TASK)).asTask().getUnchecked();
    }

    @Override
    public void stop() {
        DynamicTasks.queue(getRequiredConfig(STOP_TASK));
    }

    protected <T> T getRequiredConfig(ConfigKey<T> key) {
        return ChefConfigs.getRequiredConfig(getEntity(), key);
    }
    
}
