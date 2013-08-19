package brooklyn.util.task.ssh;

import brooklyn.location.basic.SshMachineLocation;

import com.google.common.base.Function;

/** the "Plain" class exists purely so we can massage return types for callers' convenience */
public class PlainSshTaskFactory<RET> extends AbstractSshTaskFactory<PlainSshTaskFactory<RET>,RET> {
    /** constructor where machine will be added later */
    public PlainSshTaskFactory(String ...commands) {
        super(commands);
    }

    /** convenience constructor to supply machine immediately */
    public PlainSshTaskFactory(SshMachineLocation machine, String ...commands) {
        this(commands);
        machine(machine);
    }

    @SuppressWarnings("unchecked")
    @Override
    public PlainSshTaskFactory<String> requiringZeroAndReturningStdout() {
        return (PlainSshTaskFactory<String>) super.requiringZeroAndReturningStdout();
    }

    @SuppressWarnings("unchecked")
    public <RET2> PlainSshTaskFactory<RET2> returning(Function<SshTaskWrapper<?>, RET2> resultTransformation) {
        return (PlainSshTaskFactory<RET2>) super.returning(resultTransformation);
    }
}