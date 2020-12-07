---
id: overview_metrics
title: "Metrics"
---

# Metrics

ZMX provides a `Layer` that is capable of collecting metrics from an ZIO-app and send them to a Statsd Collector (by default) or to some other reporting system (like Prometheus) with a little customization from the user. On the current version of ZMX, a push-based approach (Statsd-compatible) is the default collector in order to free the app from the additional task of collecting metrics itself and to support the collection of data from multiple nodes under a distributed setup (i.e. spark workers) without the need for having each node start their own server as needed by a pull-based approach.

In essence, the layer provides different methods such as `counter`, `timer`, `histogram`, etc. which is collected by a queue-like structure (a `RingBuffer`) and then pushed to a statsd collector either when a given `bufferSize` is reached or a given `timeout` occurs sending whatever metrics are pending if any.

Alternatively, a function of type `Chunk[Metric[_]] => IO[Exception, Chunk[Long]]` may be passed explicitly in order to, for instance, add metrics to a Prometheus `CollectorRegistry` (or whatever reporting mechanism) instead.

First, some imports needed for the examples:

```scala mdoc:silent
import zio.IO
import zio.zmx.Metrics._
import zio.duration._
import zio.zmx._
import zio.clock.Clock
import zio.console._

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.{ Counter => PCounter, Histogram => PHistogram }
import java.io.InvalidObjectException
```

## Metrics Configuration

You configure the Layer so:

```scala mdoc:silent
    val config = new MetricsConfig(
      maximumSize = 20,
      bufferSize = 5,
      timeout = 5.seconds,
      pollRate = 100.millis,
      host = None,
      port = None
    )
```
 This tells ZMX to hold a maximum of 20 items at a time, to try and process items (push to statsd or add to prometheus registry, etc.) as soon as 5 items are collected and to otherwise send whatever is in the `RingBuffer` after 5 seconds.
 
## Default (push-based) processing

 ```scala mdoc:silent
  val testSendOnTimeout = for {
    _ <- listen()
    _ <- counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
    _ <- counter("test-zmx", 3.0, 1.0, Label("test", "zmx"))
    _ <- counter("test-zmx", 5.0, 1.0, Label("test", "zmx"))
  } yield ()
``` 

## Custom Processing

If you are already instrumenting your app with Prometheus, then you can provide the Prometheus Metrics you need. We can use `labels` to reuse a single metric object at multiple points of our app, taking into account the [instrumentation best practices](https://prometheus.io/docs/practices/instrumentation/#use-labels). Then what we need is a function capable of matching a Prometheus Metric with a ZMX-metric:

```scala mdoc:silent
  val someExternalRegistry = CollectorRegistry.defaultRegistry
  val c = PCounter
    .build()
    .name("PrometheusCounter")
    .labelNames(Array("class", "method"): _*)
    .help(s"Sample prometheus counter")
    .register(someExternalRegistry)

  val h = PHistogram
    .build()
    .name("PrometheusHistogram")
    .labelNames(Array("class", "method"): _*)
    .help(s"Sample prometheus histogram")
    .register(someExternalRegistry)

  val matchMetric: Metric[_] => IO[Exception, Long] = m => {
    val e = new InvalidObjectException("Unknown Metric! Should not happen")
    val lngs = m match {
      case Metric.Counter(n, v, _, ts) =>
        IO {
          val tags = n +: (ts.map(_.value).toArray)
          c.labels(tags: _*).inc(v)
          v.toLong
        }
      case Metric.Histogram(n, v, _, ts) =>
        IO {
          val tags = n +: (ts.map(_.value).toArray)
          h.labels(tags: _*).observe(v)
          v.toLong
        }
      case _ => IO.fail(e)
    }
    lngs.orElseFail(e)
  }

  val instrument: Chunk[Metric[_]] => IO[Exception, Chunk[Long]] =
    metrics => {
      for {
        longs <- IO.foreach(metrics)(matchMetric)
      } yield { println(s"Sent: $longs"); longs }
    }
```

Then assuming the same setup as before, we just pass our custom function (`instrument`) to `listen`.

 ```scala mdoc:silent
  val testSendOnTimeoutCustom = for {
    _ <- listen(instrument)
    _ <- counter("test-zmx", 1.0, 1.0, Label("test", "zmx"))
    _ <- counter("test-zmx", 3.0, 1.0, Label("test", "zmx"))
    _ <- counter("test-zmx", 5.0, 1.0, Label("test", "zmx"))
  } yield ()
```

