package brooklyn.entity.pool;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Functions;
import com.google.common.base.Splitter;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.SoftwareProcessDriver;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import brooklyn.location.MachineLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

public class MachineEntityImpl extends SoftwareProcessImpl implements MachineEntity {

    private static final Logger LOG = LoggerFactory.getLogger(MachineEntityImpl.class);

    private transient FunctionFeed sensorFeed;

    @Override
    public void init() {
        LOG.info("Starting server pool machine with id {}", getId());
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();

        // Sensors linux-specific
        if (!getDriver().getMachine().getMachineDetails().getOsDetails().isLinux())
            return;

        sensorFeed = FunctionFeed.builder()
                .entity(this)
                .period(Duration.THIRTY_SECONDS)
                .poll(new FunctionPollConfig<Double, Double>(LOAD_AVERAGE)
                        .onFailureOrException(Functions.constant(-1d))
                        .callable(new Callable<Double>() {
                            @Override
                            public Double call() throws Exception {
                                String output = getDriver().execCommand("uptime");
                                String loadAverage = Strings.getFirstWordAfter(output, "load average:").replace(",", "");
                                return Double.valueOf(loadAverage);
                            }
                        }))
                .poll(new FunctionPollConfig<Double, Double>(CPU_USAGE)
                        .onFailureOrException(Functions.constant(0d))
                        .callable(new Callable<Double>() {
                            @Override
                            public Double call() throws Exception {
                                String output = getDriver().execCommand("cat /proc/stat");
                                List<String> cpuData = Splitter.on(" ").omitEmptyStrings().splitToList(Strings.getFirstLine(output));
                                Integer system = Integer.parseInt(cpuData.get(1));
                                Integer user = Integer.parseInt(cpuData.get(3));
                                Integer idle = Integer.parseInt(cpuData.get(4));
                                Double cpuUsage = (double) (system + user) / (double) (system + user + idle);
                                return cpuUsage * 100d;
                            }
                        }))
                .poll(new FunctionPollConfig<Long, Long>(USED_MEMORY)
                        .onFailureOrException(Functions.constant(-1L))
                        .callable(new Callable<Long>() {
                            @Override
                            public Long call() throws Exception {
                                String output = getDriver().execCommand("free | grep Mem:");
                                List<String> memoryData = Splitter.on(" ").omitEmptyStrings().splitToList(Strings.getFirstLine(output));
                                return Long.parseLong(memoryData.get(2));
                            }
                        }))
                .poll(new FunctionPollConfig<Long, Long>(FREE_MEMORY)
                        .onFailureOrException(Functions.constant(-1L))
                        .callable(new Callable<Long>() {
                            @Override
                            public Long call() throws Exception {
                                String output = getDriver().execCommand("free | grep Mem:");
                                List<String> memoryData = Splitter.on(" ").omitEmptyStrings().splitToList(Strings.getFirstLine(output));
                                return Long.parseLong(memoryData.get(3));
                            }
                        }))
                .poll(new FunctionPollConfig<Long, Long>(TOTAL_MEMORY)
                        .onFailureOrException(Functions.constant(-1L))
                        .callable(new Callable<Long>() {
                            @Override
                            public Long call() throws Exception {
                                String output = getDriver().execCommand("free | grep Mem:");
                                List<String> memoryData = Splitter.on(" ").omitEmptyStrings().splitToList(Strings.getFirstLine(output));
                                return Long.parseLong(memoryData.get(1));
                            }
                        }))
                .build();

    }

    @Override
    public void disconnectSensors() {
        disconnectServiceUpIsRunning();
        if (sensorFeed != null) sensorFeed.stop();
        super.disconnectSensors();
    }

    @Override
    public Class<?> getDriverInterface() {
        return SoftwareProcessDriver.class;
    }

    @Override
    public MachineEntitySshDriver getDriver() {
        return (MachineEntitySshDriver) super.getDriver();
    }

    @Override
    protected SoftwareProcessDriver newDriver(MachineLocation loc) {
        return new MachineEntitySshDriver(this, (SshMachineLocation) getMachineOrNull());
    }

    private static class MachineEntitySshDriver extends AbstractSoftwareProcessSshDriver {

        public MachineEntitySshDriver(EntityLocal entity, SshMachineLocation machine) {
            super(entity, machine);
        }

        @Override
        public void stop() {
        }

        @Override
        public void install() {
        }

        @Override
        public void customize() {
        }

        @Override
        public void launch() {
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        public String execCommand(String command) {
            return execCommand(command, Duration.ONE_MINUTE);
        }

        public String execCommand(String command, Duration timeout) {
            try {
                ProcessTaskWrapper<Integer> task = SshEffectorTasks.ssh(command)
                        .machine(getMachine())
                        .summary(Strings.getFirstWord(command))
                        .newTask();
                Integer result = DynamicTasks.queueIfPossible(task)
                        .executionContext(getEntity())
                        .orSubmitAsync()
                        .asTask()
                        .get(timeout);
                if (result != 0) {
                    log.warn("Command failed: {}", task.getStderr());
                    throw new IllegalStateException("Command failed, return code " + result);
                }
                return task.getStdout();
            } catch (TimeoutException te) {
                throw new IllegalStateException("Timed out running command: " + command);
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
        }
    }

}
