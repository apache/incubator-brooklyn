package brooklyn.location.jclouds;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.TemplateOptions;

import com.google.common.annotations.Beta;


/**
 * A default no-op implementation, which can be extended to override the appropriate methods.
 * 
 * Sub-classing will give the user some protection against future API changes - note that 
 * {@link JcloudsLocationCustomizer} is marked {@link Beta}.
 * 
 * @author aled
 */
public class BasicJcloudsLocationCustomizer implements JcloudsLocationCustomizer {

    @Override
    public void customize(JcloudsLocation location, ComputeService computeService, TemplateBuilder templateBuilder) {
        customize(computeService, templateBuilder);
    }

    @Override
    public void customize(JcloudsLocation location, ComputeService computeService, TemplateOptions templateOptions) {
        customize(computeService, templateOptions);
    }

    @Override
    public void customize(JcloudsLocation location, ComputeService computeService, JcloudsSshMachineLocation machine) {
        customize(computeService, machine);
    }
    
    /**
     * @deprecated since 0.6; use {@link #customize(JcloudsLocation, ComputeService, TemplateBuilder)
     */
    @Deprecated
    protected void customize(ComputeService computeService, TemplateBuilder templateBuilder) {
        // no-op
    }

    /**
     * @deprecated since 0.6; use {@link #customize(JcloudsLocation, ComputeService, TemplateOptions)
     */
    @Deprecated
    public void customize(ComputeService computeService, TemplateOptions templateOptions) {
        // no-op
    }

    /**
     * @deprecated since 0.6; use {@link #customize(JcloudsLocation, ComputeService, JcloudsSshMachineLocation)
     */
    @Deprecated
    public void customize(ComputeService computeService, JcloudsSshMachineLocation machine) {
        // no-op
    }
}
