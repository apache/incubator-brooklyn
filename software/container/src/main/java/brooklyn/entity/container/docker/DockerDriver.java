package brooklyn.entity.container.docker;

import brooklyn.entity.basic.SoftwareProcessDriver;

/**
 *
 * The {@link SoftwareProcessDriver} for Docker.
 *
 * @author Andrea Turli
 */
public interface DockerDriver extends SoftwareProcessDriver {

   Integer getDockerPort();

}
