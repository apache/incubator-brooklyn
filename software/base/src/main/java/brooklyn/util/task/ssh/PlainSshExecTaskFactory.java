package brooklyn.util.task.ssh;

import brooklyn.location.basic.SshMachineLocation;

import com.google.common.base.Function;

/** the "Plain" class exists purely so we can massage return types for callers' convenience */
public class PlainSshExecTaskFactory<RET> extends AbstractSshExecTaskFactory<PlainSshExecTaskFactory<RET>,RET> {
    /** constructor where machine will be added later */
    public PlainSshExecTaskFactory(String ...commands) {
        super(commands);
    }

    /** convenience constructor to supply machine immediately */
    public PlainSshExecTaskFactory(SshMachineLocation machine, String ...commands) {
        this(commands);
        machine(machine);
    }

    @SuppressWarnings("unchecked")
    @Override
    public PlainSshExecTaskFactory<String> requiringZeroAndReturningStdout() {
        return (PlainSshExecTaskFactory<String>) super.requiringZeroAndReturningStdout();
    }

    @SuppressWarnings("unchecked")
    public <RET2> PlainSshExecTaskFactory<RET2> returning(Function<SshExecTaskWrapper<?>, RET2> resultTransformation) {
        return (PlainSshExecTaskFactory<RET2>) super.returning(resultTransformation);
    }
}