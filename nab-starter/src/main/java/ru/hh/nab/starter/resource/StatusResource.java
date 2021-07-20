package ru.hh.nab.starter.resource;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import ru.hh.nab.starter.AppMetadata;

@Path("")
@Singleton
public class StatusResource {
  
  private final AppMetadata appMetaData;

  @Inject
  public StatusResource(AppMetadata appMetaData) {
    this.appMetaData = appMetaData;
  }

  @GET
  @Produces(MediaType.TEXT_XML)
  public String status() {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<project name=\"" + appMetaData.getServiceName() + "\">\n"
        + " <version>" + appMetaData.getVersion() + "</version>\n"
        + " <uptime>" + appMetaData.getUpTimeSeconds() + "</uptime>\n"
        + "</project>\n";
  }
}
