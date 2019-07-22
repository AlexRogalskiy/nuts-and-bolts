package ru.hh.nab.starter.filters;

import static javax.ws.rs.core.Response.Status.OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import ru.hh.nab.common.mdc.MDC;
import ru.hh.nab.starter.NabApplication;
import ru.hh.nab.testbase.NabTestBase;
import ru.hh.nab.testbase.NabTestConfig;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@ContextConfiguration(classes = {NabTestConfig.class})
public class ResourceNameLoggingFilterTest extends NabTestBase {

  @Override
  protected NabApplication getApplication() {
    return NabApplication.builder().configureJersey(SpringCtxForJersey.class).bindTo("/test/*").build();
  }

  @Test
  public void testResourceName() {
    assertFalse(MDC.getController().isPresent());

    Response response = executeGet("/test/test");

    assertEquals(OK.getStatusCode(), response.getStatus());

    assertEquals("TestResource#test", response.readEntity(String.class));
    assertFalse(MDC.getController().isPresent());
  }

  @Path("/test")
  public static class TestResource {
    @GET
    public String test() {
      assertTrue(MDC.getController().isPresent());
      return MDC.getController().get();
    }
  }

  @Configuration
  @Import(TestResource.class)
  static class SpringCtxForJersey {
  }
}
