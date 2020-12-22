---
id: metrics_statsd
title: "StatsD Reporting"
---

```scala mdoc:invisible
import zio._
import zio.duration._
import zio.zmx.metrics._

import zio.zmx.statsd.StatsdInstrumentation
import zio.zmx.MetricsConfig
import zio.zmx.InstrumentedSample
```
## The ZMX StatsD reporter

To run the instrumentation example above reporting to statsd, we have to inject a statsd reporter with a proper configuration. The important piece in the code 
below is the host and the port, which is the UDP adress of a statsd collector. 

```scala mdoc:silent
object StatsdInstrumentedApp extends ZmxApp with InstrumentedSample {

  private val config = MetricsConfig(
    maximumSize = 1024,
    bufferSize = 1024,
    timeout = 10.seconds,
    pollRate = 1.second,
    host = Some("localhost"),
    port = Some(8125)
  )

  override def makeInstrumentation = StatsdInstrumentation.make(config)

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    program
}
```

Whenever something is counted, a statsd datagram is sent to the collector and all further processing will be done within the StatsD environment. 
