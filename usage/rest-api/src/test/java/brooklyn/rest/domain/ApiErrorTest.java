package brooklyn.rest.domain;

import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.io.IOException;

import org.testng.annotations.Test;
import org.testng.util.Strings;

public class ApiErrorTest {

    @Test
    public void testSerializeApiError() throws IOException {
        ApiError error = ApiError.builder()
                .message("explanatory message")
                .details("accompanying details")
                .build();
        assertEquals(asJson(error), jsonFixture("fixtures/api-error-basic.json"));
    }

    @Test
    public void testSerializeApiErrorFromThrowable() throws IOException {
        Exception e = new Exception("error");
        e.setStackTrace(Thread.currentThread().getStackTrace());

        ApiError error = ApiError.fromThrowable(e).build();
        ApiError deserialised = fromJson(asJson(error), ApiError.class);

        assertFalse(Strings.isNullOrEmpty(deserialised.getDetails()), "Expected details to contain exception stack trace");
        assertEquals(deserialised, error);
    }

    @Test
    public void testSerializeApiErrorWithoutDetails() throws IOException {
        ApiError error = ApiError.builder()
                .message("explanatory message")
                .build();
        assertEquals(asJson(error), jsonFixture("fixtures/api-error-no-details.json"));
    }

}
