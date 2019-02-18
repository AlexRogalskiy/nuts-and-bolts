package ru.hh.nab.logging;

import ch.qos.logback.classic.BasicConfigurator;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.event.Level;

@SuppressWarnings("rawtypes")
public abstract class NabLoggingConfiguratorTemplate extends BasicConfigurator {

  @Override
  public final void configure(LoggerContext lc) {
    Properties properties = createLoggingProperties();
    configure(new LoggingContextWrapper(lc, properties));
  }

  protected abstract Properties createLoggingProperties();

  public abstract void configure(LoggingContextWrapper context);

  protected <A extends Appender> A createAppender(LoggingContextWrapper context, String name, Supplier<A> instanceCreator) {
    A appender = instanceCreator.get();
    appender.setName(name);
    appender.setContext(context.getContext());
    context.getContext().register(appender);
    addInfo("Created appender with name " + name + " of type " + appender.getClass());
    appender.start();
    addInfo("Appender with name " + name + " started");
    return appender;
  }

  protected LoggerWrapper createLogger(LoggingContextWrapper context, Class<?> cls, Level level, Appender appender) {
    return createLogger(context, cls, level, false, Set.of(appender));
  }

  protected LoggerWrapper createLogger(LoggingContextWrapper context, Class<?> cls, Level level, boolean additivity, Appender appender) {
    return createLogger(context, cls, level, additivity, Set.of(appender));
  }

  protected LoggerWrapper createLogger(LoggingContextWrapper context, Class<?> cls, Level level, Appender appender, Appender appender2) {
    return createLogger(context, cls, level, false, Set.of(appender, appender2));
  }

  protected LoggerWrapper createLogger(LoggingContextWrapper context, Class<?> cls, Level level, boolean additivity, Appender appender,
      Appender appender2) {
    return createLogger(context, cls, level, additivity, Set.of(appender, appender2));
  }

  protected LoggerWrapper createLogger(LoggingContextWrapper context, Class<?> cls, Level level, Appender appender, Appender appender2,
      Appender appender3) {
    return createLogger(context, cls, level, false, Set.of(appender, appender2, appender3));
  }

  protected LoggerWrapper createLogger(LoggingContextWrapper context, Class<?> cls, Level level, Appender appender, boolean additivity,
      Appender appender2, Appender appender3) {
    return createLogger(context, cls, level, additivity, Set.of(appender, appender2, appender3));
  }

  protected LoggerWrapper createLogger(LoggingContextWrapper context, Class<?> aClass, Level level, boolean additivity,
      Collection<Appender> appenders) {
    return createLogger(context, aClass.getName(), level, additivity, appenders);
  }

  protected LoggerWrapper createLogger(LoggingContextWrapper context, String name, Level level, boolean additivity,
      Collection<Appender> appenders) {
    var logger = context.getContext().getLogger(name);
    logger.setLevel(ch.qos.logback.classic.Level.toLevel(level.toString()));
    logger.setAdditive(additivity);
    appenders.forEach(logger::addAppender);
    addInfo("Created logger for name " + name + "level=" + level + ", additivity=" + additivity + ". appenders="
      + appenders.stream().map(Appender::getName).collect(Collectors.joining(",")));
    return new LoggerWrapper(logger);
  }

  protected static Properties loadPropertiesFile(Path path) {
    var properties = new Properties();
    try {
      properties.load(Files.newBufferedReader(path));
      return properties;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static void setPropertyIfNotSet(Properties properties, String key, String value) {
    var existingValue = properties.getProperty(key);
    if (StringUtils.isNotEmpty(existingValue)) {
      return;
    }
    properties.setProperty(key, value);
  }

  protected LoggerWrapper getRootLogger(LoggingContextWrapper context) {
    return new LoggerWrapper(context.getContext().getLogger(Logger.ROOT_LOGGER_NAME));
  }

  public static final class LoggerWrapper {
    private final Logger logger;

    private LoggerWrapper(Logger logger) {
      this.logger = logger;
    }

    public void setLevel(Level level) {
      logger.setLevel(ch.qos.logback.classic.Level.valueOf(level.toString()));
    }

    public void setAdditivity(boolean additivity) {
      logger.setAdditive(additivity);
    }

    public void addAppenders(Appender... appenders) {
      Stream.of(appenders).forEach(logger::addAppender);
    }
  }

  public final class LoggingContextWrapper {

    private final LoggerContext context;

    private LoggingContextWrapper(LoggerContext context, Properties properties) {
      properties.stringPropertyNames().forEach(propertyKey -> {
        context.putProperty(propertyKey, properties.getProperty(propertyKey));
        addInfo("Put property " + String.join("=", propertyKey, properties.getProperty(propertyKey)));
      });
      this.context = context;
    }

    private LoggerContext getContext() {
      return context;
    }

    public String getProperty(String key, String defaultValue) {
      return Optional.ofNullable(context.getProperty(key)).orElse(defaultValue);
    }

    public void log(String msg) {
      addInfo(msg);
    }
  }
}
