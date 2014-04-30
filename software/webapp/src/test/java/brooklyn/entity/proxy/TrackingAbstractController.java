package brooklyn.entity.proxy;

import java.util.Collection;
import java.util.List;

import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(TrackingAbstractControllerImpl.class)
public interface TrackingAbstractController extends AbstractController {
    List<Collection<String>> getUpdates();
}
