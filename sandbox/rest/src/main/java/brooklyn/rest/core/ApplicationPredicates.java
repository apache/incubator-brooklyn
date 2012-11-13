package brooklyn.rest.core;

import brooklyn.rest.api.ApplicationSummary;
import com.google.common.base.Predicate;

import javax.annotation.Nullable;

public class ApplicationPredicates {

  public static Predicate<ApplicationSummary> status(final ApplicationSummary.Status status) {
    return new Predicate<ApplicationSummary>() {
      @Override
      public boolean apply(@Nullable ApplicationSummary app) {
        return app != null && app.getStatus() == status;
      }
    };
  }

}
