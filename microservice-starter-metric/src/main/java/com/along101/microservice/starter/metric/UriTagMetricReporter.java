package com.along101.microservice.starter.metric;

import com.codahale.metrics.*;
import com.codahale.metrics.Timer;
import com.along101.dropwizard.metrics.builder.KairosdbMetricBuilder;
import com.along101.dropwizard.metrics.builder.along101Metric;
import com.along101.dropwizard.metrics.transport.KafkaTransport;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class UriTagMetricReporter extends ScheduledReporter {

    private final KafkaTransport kafkaTransport;
    private String applicationId;
    private final Map<String, String> tags;
    private Map<String, AtomicLong> lastReport = new ConcurrentHashMap<>();

    public static Builder forRegistry(MetricRegistry registry) {
        return new Builder(registry);
    }

    protected UriTagMetricReporter(MetricRegistry registry, String name, MetricFilter filter, TimeUnit rateUnit, TimeUnit durationUnit, Map<String, String> tags, KafkaTransport kafkaTransport, String applicationId) {
        super(registry, name, filter, rateUnit, durationUnit);
        this.kafkaTransport = kafkaTransport;
        this.applicationId = applicationId;
        this.tags = tags;
    }

    public static class Builder {
        private final MetricRegistry registry;
        private String name;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private MetricFilter filter;
        private Map<String, String> tags;
        private String applicationId;

        private Builder(MetricRegistry registry) {
            this.registry = registry;
            this.rateUnit = TimeUnit.MILLISECONDS;
            this.durationUnit = TimeUnit.MILLISECONDS;
            this.filter = MetricFilter.ALL;
            this.tags = new HashMap<>();
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder convertRatesTo(TimeUnit rateUnit) {
            this.rateUnit = rateUnit;
            return this;
        }

        public Builder convertDurationsTo(TimeUnit durationUnit) {
            this.durationUnit = durationUnit;
            return this;
        }

        public Builder filter(MetricFilter filter) {
            this.filter = filter;
            return this;
        }

        public Builder withTags(Map<String, String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder withApplicationId(String applicationId) {
            this.applicationId = applicationId;
            return this;
        }

        public UriTagMetricReporter build(KafkaTransport kafkaTransport) {
            return new UriTagMetricReporter(registry, name, filter,
                    rateUnit, durationUnit, tags,
                    kafkaTransport, applicationId);
        }
    }

    @Override
    public void report(SortedMap<String, Gauge> gauges, SortedMap<String, Counter> counters, SortedMap<String, Histogram> histograms, SortedMap<String, Meter> meters, SortedMap<String, Timer> timers) {
        final long timestamp = System.currentTimeMillis();
        final Set<along101Metric> metrics = new HashSet<>();

        for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
            metrics.addAll(buildGauge(entry.getKey(), entry.getValue(), timestamp, tags));
        }

        for (Map.Entry<String, Counter> entry : counters.entrySet()) {
            metrics.addAll(buildCounter(entry.getKey(), entry.getValue(), timestamp, tags));
        }

        for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
            metrics.addAll(buildHistograms(entry.getKey(), entry.getValue(), timestamp, tags));
        }

        for (Map.Entry<String, Meter> entry : meters.entrySet()) {
            metrics.addAll(buildMeters(entry.getKey(), entry.getValue(), timestamp, tags));
        }

        for (Map.Entry<String, Timer> entry : timers.entrySet()) {
            metrics.addAll(buildTimers(entry.getKey(), entry.getValue(), timestamp, tags));
        }

        kafkaTransport.send(applicationId, metrics);
    }

    private Set<along101Metric> buildTimers(String name, Timer timer, long timestamp, Map<String, String> tags) {
        final MetricsCollector collector = MetricsCollector.createNew(name, tags, timestamp);
        final Snapshot snapshot = timer.getSnapshot();
        if (getChangeCount(name, timer.getCount()) == 0) {
            return Collections.emptySet();
        }
        return collector.addMetric("count", timer.getCount())
                //convert rate
                .addMetric("m15", convertRate(timer.getFifteenMinuteRate()))
                .addMetric("m5", convertRate(timer.getFiveMinuteRate()))
                .addMetric("m1", convertRate(timer.getOneMinuteRate()))
                .addMetric("mean_rate", convertRate(timer.getMeanRate()))
                // convert duration
                .addMetric("max", convertDuration(snapshot.getMax()))
                .addMetric("min", convertDuration(snapshot.getMin()))
                .addMetric("mean", convertDuration(snapshot.getMean()))
                .addMetric("stddev", convertDuration(snapshot.getStdDev()))
                .addMetric("median", convertDuration(snapshot.getMedian()))
                .addMetric("p75", convertDuration(snapshot.get75thPercentile()))
                .addMetric("p95", convertDuration(snapshot.get95thPercentile()))
                .addMetric("p98", convertDuration(snapshot.get98thPercentile()))
                .addMetric("p99", convertDuration(snapshot.get99thPercentile()))
                .addMetric("p999", convertDuration(snapshot.get999thPercentile()))
                .build();
    }

    private Set<along101Metric> buildHistograms(String name, Histogram histogram, long timestamp, Map<String, String> tags) {
        final MetricsCollector collector = MetricsCollector.createNew(name, tags, timestamp);
        final Snapshot snapshot = histogram.getSnapshot();
        if (getChangeCount(name, histogram.getCount()) == 0) {
            return Collections.emptySet();
        }
        return collector.addMetric("count", histogram.getCount())
                .addMetric("max", snapshot.getMax())
                .addMetric("min", snapshot.getMin())
                .addMetric("mean", snapshot.getMean())
                .addMetric("stddev", snapshot.getStdDev())
                .addMetric("median", snapshot.getMedian())
                .addMetric("p75", snapshot.get75thPercentile())
                .addMetric("p95", snapshot.get95thPercentile())
                .addMetric("p98", snapshot.get98thPercentile())
                .addMetric("p99", snapshot.get99thPercentile())
                .addMetric("p999", snapshot.get999thPercentile())
                .build();
    }

    private Set<along101Metric> buildMeters(String name, Meter meter, long timestamp, Map<String, String> tags) {
        final MetricsCollector collector = MetricsCollector.createNew(name, tags, timestamp);
        if (getChangeCount(name, meter.getCount()) == 0) {
            return Collections.emptySet();
        }
        return collector.addMetric("count", meter.getCount())
                // convert rate
                .addMetric("mean_rate", convertRate(meter.getMeanRate()))
                .addMetric("m1", convertRate(meter.getOneMinuteRate()))
                .addMetric("m5", convertRate(meter.getFiveMinuteRate()))
                .addMetric("m15", convertRate(meter.getFifteenMinuteRate()))
                .build();
    }

    private Set<along101Metric> buildCounter(String name, Counter counter, long timestamp, Map<String, String> tags) {
        final MetricsCollector collector = MetricsCollector.createNew(name, tags, timestamp);
        long changedCount = getChangeCount(name, counter.getCount());
        if (changedCount == 0) {
            return Collections.emptySet();
        }
        return collector.addMetric("value", changedCount).build();
    }

    private Set<along101Metric> buildGauge(String name, Gauge gauge, long timestamp, Map<String, String> tags) {
        final MetricsCollector collector = MetricsCollector.createNew(name, tags, timestamp);
        return collector.addMetric("value", gauge.getValue()).build();
    }

    private long getChangeCount(String name, long count) {
        this.lastReport.putIfAbsent(name, new AtomicLong(0));
        AtomicLong last = this.lastReport.get(name);
        long lastCount = last.getAndSet(count);
        return count - lastCount;
    }

    private static class MetricsCollector {
        private final String name;
        private final Map<String, String> tags;
        private final long timestamp;
        private final Set<along101Metric> metrics = new HashSet<>();

        private MetricsCollector(String name, Map<String, String> tags, long timestamp) {
            TagName tagName = TagNameUtil.parse(name);
            this.name = tagName.getName();
            this.tags = tagName.getTags();
            this.tags.putAll(tags);
            this.timestamp = timestamp;
        }

        public static MetricsCollector createNew(String name, Map<String, String> tags, long timestamp) {
            return new MetricsCollector(name, tags, timestamp);
        }

        public MetricsCollector addMetric(String metricName, Object value) {
            String name = StringUtils.isBlank(metricName) ? this.name : this.name + "." + metricName;
            along101Metric metric = new KairosdbMetricBuilder.Builder(name)
                    .withTags(tags).withTimestamp(timestamp).withValue(value).build();
            this.metrics.add(metric);
            return this;
        }

        public Set<along101Metric> build() {
            return metrics;
        }
    }

}
