package org.apache.brooklyn.test.framework;

import org.apache.brooklyn.api.entity.ImplementedBy;

/**
 * Entity that logically groups other test entities
 *
 * @author m4rkmckenna
 */
@ImplementedBy(value = TestCaseImpl.class)
public interface TestCase extends BaseTest {
}
