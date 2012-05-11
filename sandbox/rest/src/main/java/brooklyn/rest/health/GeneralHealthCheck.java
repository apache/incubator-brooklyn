package brooklyn.rest.health;

import com.yammer.metrics.core.HealthCheck;

public class GeneralHealthCheck extends HealthCheck {

  public GeneralHealthCheck() {
    super("general");
  }

  @Override
  protected Result check() throws Exception {
    return Result.healthy();
  }
}
