package brooklyn.location.jclouds;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.TemplateOptions;


import com.google.common.annotations.Beta;

/**
 * Customization hooks to allow apps to perform specific customisation at each stage of jclouds machine provisioning.
 * For example, an app could attach an EBS volume to an EC2 node, or configure a desired availability zone.
 */
@Beta
public interface JcloudsLocationCustomizer { 
    
    void customize(JcloudsLocation location, ComputeService computeService, TemplateBuilder templateBuilder);
    void customize(JcloudsLocation location, ComputeService computeService, TemplateOptions templateOptions);
    void customize(JcloudsLocation location, ComputeService computeService, JcloudsSshMachineLocation machine);
}
