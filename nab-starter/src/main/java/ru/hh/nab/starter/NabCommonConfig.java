package ru.hh.nab.starter;

import com.timgroup.statsd.StatsDClient;
import static java.util.Optional.ofNullable;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Predicate;
import javax.inject.Named;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import ru.hh.nab.common.executor.ScheduledExecutor;
import ru.hh.nab.common.properties.FileSettings;
import ru.hh.nab.metrics.StatsDSender;
import ru.hh.nab.starter.metrics.JvmMetricsSender;
import static ru.hh.nab.starter.server.jetty.JettyServerFactory.createJettyThreadPool;
import static ru.hh.nab.starter.server.jetty.JettySettingsConstants.JETTY;
import ru.hh.nab.starter.server.jetty.MonitoredQueuedThreadPool;

@Configuration
public class NabCommonConfig {
  public static final String SERVICE_NAME_PROPERTY = "serviceName";
  public static final String NODE_NAME_PROPERTY = "nodeName";
  public static final String DATACENTER_NAME_PROPERTY = "datacenter";

  @Named(SERVICE_NAME_PROPERTY)
  @Bean(SERVICE_NAME_PROPERTY)
  String serviceName(FileSettings fileSettings) {
    return ofNullable(fileSettings.getString(SERVICE_NAME_PROPERTY)).filter(Predicate.not(String::isEmpty))
      .orElseThrow(() -> new RuntimeException(String.format("'%s' property is not found in file settings", SERVICE_NAME_PROPERTY)));
  }

  @Named(DATACENTER_NAME_PROPERTY)
  @Bean(DATACENTER_NAME_PROPERTY)
  String datacenter(FileSettings fileSettings) {
    return ofNullable(fileSettings.getString(DATACENTER_NAME_PROPERTY)).filter(Predicate.not(String::isEmpty))
      .orElseThrow(() -> new RuntimeException(String.format("'%s' property is not found in file settings", DATACENTER_NAME_PROPERTY)));
  }

  @Named(NODE_NAME_PROPERTY)
  @Bean(NODE_NAME_PROPERTY)
  String nodeName(FileSettings fileSettings) {
    return ofNullable(fileSettings.getString(NODE_NAME_PROPERTY)).filter(Predicate.not(String::isEmpty))
      .orElseThrow(() -> new RuntimeException(String.format("'%s' property is not found in file settings", NODE_NAME_PROPERTY)));
  }

  @Bean
  MonitoredQueuedThreadPool jettyThreadPool(FileSettings fileSettings,
    @Named(SERVICE_NAME_PROPERTY) String serviceNameValue,
    StatsDSender statsDSender
  ) throws Exception {
    return createJettyThreadPool(fileSettings.getSubSettings(JETTY), serviceNameValue, statsDSender);
  }

  @Bean
  FileSettings fileSettings(Properties serviceProperties) {
    return new FileSettings(serviceProperties);
  }

  @Bean
  ScheduledExecutorService scheduledExecutorService() {
    return new ScheduledExecutor();
  }

  @Bean
  StatsDSender statsDSender(ScheduledExecutorService scheduledExecutorService, StatsDClient statsDClient,
                            @Named(SERVICE_NAME_PROPERTY) String serviceNameValue, FileSettings fileSettings) {
    StatsDSender statsDSender = new StatsDSender(statsDClient, scheduledExecutorService);
    if (Boolean.TRUE.equals(fileSettings.getBoolean("metrics.jvm.enabled"))) {
      JvmMetricsSender.create(statsDSender, serviceNameValue);
    }
    return statsDSender;
  }

  @Bean
  PropertiesFactoryBean projectProperties() {
    PropertiesFactoryBean projectProps = new PropertiesFactoryBean();
    projectProps.setLocation(new ClassPathResource(AppMetadata.PROJECT_PROPERTIES));
    projectProps.setIgnoreResourceNotFound(true);
    return projectProps;
  }

  @Bean
  AppMetadata appMetadata(String serviceName, Properties projectProperties) {
    return new AppMetadata(serviceName, projectProperties);
  }
}
