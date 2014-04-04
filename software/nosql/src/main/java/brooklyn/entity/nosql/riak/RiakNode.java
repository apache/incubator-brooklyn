package brooklyn.entity.nosql.riak;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(RiakNodeImpl.class)
public interface RiakNode extends SoftwareProcess {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION,
            "1.4.8");

    @SetFromFlag("riakVmArgsTemplateUrl")
    ConfigKey<String> RIAK_VM_ARGS_TEMPLATE_URL = ConfigKeys.newStringConfigKey(
            "riak.vm.args.template.url", "Template file (in freemarker format) for the vm.args config file",
            "classpath://brooklyn/entity/nosql/riak/vm.args");

}
