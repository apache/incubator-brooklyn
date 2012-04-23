package brooklyn.rest.core;

import brooklyn.rest.api.Application;
import com.google.common.base.Predicate;
import javax.annotation.Nullable;

public class ApplicationPredicates {

  public static Predicate<Application> status(final Application.Status status) {
    return new Predicate<Application>() {
      @Override
      public boolean apply(@Nullable Application app) {
        return app != null && app.getStatus() == status;
      }
    };
  }

}
