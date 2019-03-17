package ru.hh.nab.datasource.monitoring;

import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.PoolStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.nab.common.mdc.MDC;
import ru.hh.nab.common.properties.FileSettings;
import ru.hh.nab.datasource.monitoring.stack.CompressedStackFactory;
import ru.hh.nab.datasource.monitoring.stack.CompressedStackFactoryConfig;
import ru.hh.nab.metrics.Counters;
import ru.hh.nab.metrics.Histogram;
import ru.hh.nab.metrics.StatsDSender;
import ru.hh.nab.metrics.Tag;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static java.util.Optional.ofNullable;
import static ru.hh.nab.datasource.DataSourceSettings.MONITORING_LONG_CONNECTION_USAGE_MS;
import static ru.hh.nab.datasource.DataSourceSettings.MONITORING_SEND_SAMPLED_STATS;
import static ru.hh.nab.datasource.monitoring.ConnectionPoolMetrics.ACQUISITION_MS;
import static ru.hh.nab.datasource.monitoring.ConnectionPoolMetrics.ACTIVE_CONNECTIONS;
import static ru.hh.nab.datasource.monitoring.ConnectionPoolMetrics.CONNECTION_TIMEOUTS;
import static ru.hh.nab.datasource.monitoring.ConnectionPoolMetrics.CREATION_MS;
import static ru.hh.nab.datasource.monitoring.ConnectionPoolMetrics.IDLE_CONNECTIONS;
import static ru.hh.nab.datasource.monitoring.ConnectionPoolMetrics.MAX_CONNECTIONS;
import static ru.hh.nab.datasource.monitoring.ConnectionPoolMetrics.MIN_CONNECTIONS;
import static ru.hh.nab.datasource.monitoring.ConnectionPoolMetrics.PENDING_THREADS;
import static ru.hh.nab.datasource.monitoring.ConnectionPoolMetrics.SAMPLED_USAGE_MS;
import static ru.hh.nab.datasource.monitoring.ConnectionPoolMetrics.TOTAL_CONNECTIONS;
import static ru.hh.nab.datasource.monitoring.ConnectionPoolMetrics.TOTAL_USAGE_MS;
import static ru.hh.nab.datasource.monitoring.ConnectionPoolMetrics.USAGE_MS;

public class NabMetricsTrackerFactory implements MetricsTrackerFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(NabMetricsTrackerFactory.class);

  private final String serviceName;
  private final StatsDSender statsDSender;
  private final boolean sendSampledStats;
  private final Integer longConnectionUsageMs;
  private final CompressedStackFactoryConfig compressedStackFactoryConfig;

  public NabMetricsTrackerFactory(String serviceName, StatsDSender statsDSender, CompressedStackFactoryConfig compressedStackFactoryConfig,
                                  FileSettings dataSourceSettings) {
    this.serviceName = serviceName;
    this.statsDSender = statsDSender;
    this.sendSampledStats = ofNullable(dataSourceSettings.getBoolean(MONITORING_SEND_SAMPLED_STATS)).orElse(Boolean.FALSE);
    this.longConnectionUsageMs = dataSourceSettings.getInteger(MONITORING_LONG_CONNECTION_USAGE_MS);
    this.compressedStackFactoryConfig = compressedStackFactoryConfig;
  }

  @Override
  public IMetricsTracker create(String poolName, PoolStats poolStats) {
    return new MonitoringMetricsTracker(poolName, poolStats);
  }

  class MonitoringMetricsTracker implements IMetricsTracker {
    private final String poolName;
    private final Counters usageCounters, timeoutCounters, sampledUsageCounters;
    private final Histogram creationHistogram, acquisitionHistogram, usageHistogram;
    private final CompressedStackFactory compressedStackFactory;

    MonitoringMetricsTracker(String poolName, PoolStats poolStats) {
      this.poolName = poolName;

      creationHistogram = new Histogram(2000);
      acquisitionHistogram = new Histogram(2000);
      usageHistogram = new Histogram(2000);
      usageCounters = new Counters(500);
      timeoutCounters = new Counters(500);

      statsDSender.sendPercentilesPeriodically(getMetricName(poolName, CREATION_MS), creationHistogram, 95, 99, 100);
      statsDSender.sendPercentilesPeriodically(getMetricName(poolName, ACQUISITION_MS), acquisitionHistogram, 95, 99, 100);
      statsDSender.sendPercentilesPeriodically(getMetricName(poolName, USAGE_MS), usageHistogram, 95, 99, 100);
      statsDSender.sendCountersPeriodically(getMetricName(poolName, TOTAL_USAGE_MS), usageCounters);
      statsDSender.sendCountersPeriodically(getMetricName(poolName, CONNECTION_TIMEOUTS), timeoutCounters);

      statsDSender.sendMetricPeriodically(getMetricName(poolName, ACTIVE_CONNECTIONS), () -> (long) poolStats.getActiveConnections());
      statsDSender.sendMetricPeriodically(getMetricName(poolName, TOTAL_CONNECTIONS), () -> (long) poolStats.getTotalConnections());
      statsDSender.sendMetricPeriodically(getMetricName(poolName, IDLE_CONNECTIONS), () -> (long) poolStats.getIdleConnections());
      statsDSender.sendMetricPeriodically(getMetricName(poolName, MAX_CONNECTIONS), () -> (long) poolStats.getMaxConnections());
      statsDSender.sendMetricPeriodically(getMetricName(poolName, MIN_CONNECTIONS), () -> (long) poolStats.getMinConnections());
      statsDSender.sendMetricPeriodically(getMetricName(poolName, PENDING_THREADS), () -> (long) poolStats.getPendingThreads());

      if (sendSampledStats) {
        compressedStackFactory = new CompressedStackFactory(compressedStackFactoryConfig);
        sampledUsageCounters = new Counters(2000);
        statsDSender.sendCountersPeriodically(getMetricName(poolName, SAMPLED_USAGE_MS), sampledUsageCounters);
      } else {
        sampledUsageCounters = null;
        compressedStackFactory = null;
      }
    }

    @Override
    public void recordConnectionCreatedMillis(long connectionCreatedMillis) {
      creationHistogram.save((int) connectionCreatedMillis);
    }

    @Override
    public void recordConnectionAcquiredNanos(final long elapsedAcquiredNanos) {
      acquisitionHistogram.save((int) TimeUnit.NANOSECONDS.toMillis(elapsedAcquiredNanos));
    }

    @Override
    public void recordConnectionUsageMillis(final long elapsedBorrowedMillis) {
      int connectionUsageMs = (int) elapsedBorrowedMillis;

      if (longConnectionUsageMs != null && connectionUsageMs >= longConnectionUsageMs) {
        String message = String.format(
          "%s connection was used for more than %d ms (%d ms), not fatal, but should be fixed",
          poolName, longConnectionUsageMs, connectionUsageMs
        );
        LOGGER.error(message, new RuntimeException(poolName + " connection usage duration exceeded"));
      }

      Tag controllerTag = new Tag("controller", MDC.getController().orElse("unknown"));
      usageCounters.add(connectionUsageMs, controllerTag);
      usageHistogram.save(connectionUsageMs);

      if (sendSampledStats && ThreadLocalRandom.current().nextInt(100) == 0) {
        sampledUsageCounters.add(connectionUsageMs, new Tag("stack", compressedStackFactory.create()));
      }
    }

    @Override
    public void recordConnectionTimeout() {
      timeoutCounters.add(1);
    }
  }

  private String getMetricName(String dataSourceName, String metricName) {
    return serviceName + '.' + dataSourceName + '.' + metricName;
  }
}
