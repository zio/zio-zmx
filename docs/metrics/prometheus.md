---
id: metrics_prometheus
title: "Prometheus Example"
---

## The ZMX Prometheus example

```scala mdoc:invisible
import java.net.InetSocketAddress
import uzhttp.server.Server
import uzhttp._
import zio._
import zio.random._
import zio.duration._
import zio.console._
import zio.zmx.metrics._
import zio.zmx.prometheus._

```

To demonstrate the unified ZMX reporting API we will use the example below which uses only ZMX defined methods 
to capture some metrics. 

```scala mdoc:silent
import zio.zmx._

trait InstrumentedSample {

  // Count something explicitly
  private lazy val doSomething =
    incrementCounter("myCounter", 1.0d, "effect" -> "count1")

  // Manipulate an arbitrary Gauge
  private lazy val gaugeSomething = for {
    v1 <- nextDoubleBetween(0.0d, 100.0d)
    v2 <- nextDoubleBetween(-50d, 50d)
    _  <- setGauge("setGauge", v1)
    _  <- adjustGauge("adjustGauge", v2)
  } yield ()

  // Use a convenient extension to count the number of executions of an effect
  // In this particular case count how often the gauge has been set
  private lazy val doSomething2 = gaugeSomething.counted("myCounter", "effect" -> "count2")

  def program: ZIO[ZEnv, Nothing, ExitCode] = for {
    _ <- doSomething.schedule(Schedule.spaced(100.millis)).forkDaemon
    _ <- doSomething2.schedule(Schedule.spaced(200.millis)).forkDaemon
  } yield ExitCode.success
}
```

We have 2 counters which differ only in the value of the `effect` label, but otherwise refer to the same name. The counter 
labeled `count1` is incremented explicitly, while the counter labeled `count2` will count the number of executions of 
`gaugeSomething`.  

We also have two gauges, one of which is regularly set to an absolute value while the other is modified with a random delta. 

The `program` effect simply kicks off the effects simulating the metrics. The mainline of the programm should have some 
logic to terminate, either time or event based. 

### A mainline for the Prometheus instrumentation. 

In order to have a working example, we need to provide a mainline which executes the `program` and uses the `report`
effect of the prometheus instrumentation to provide an HTTP endpoint serving the premetheus encoded metrics. 

In our example we are using [uzhttp](https://github.com/polynote/uzhttp) for its simplicity. 

```scala mdoc:silent
object PrometheusInstrumentedApp extends PrometheusApp with InstrumentedSample {

  private val bindHost = "127.0.0.1"
  private val bindPort = 8080

  private val demoQs: Seq[Quantile] =
    1.to(9).map(i => i / 10d).map(d => Quantile(d, 0.03)).collect { case Some(q) => q }

  // For all histograms set the buckets to some linear slots
  private val cfg                   = PrometheusConfig(
    // Use a linear bucket scale for all histograms
    buckets = Chunk(_ => Some(PMetric.Buckets.Linear(10, 10, 10))),
    // Use the demo Quantiles for all summaries
    quantiles = Chunk(_ => Some(demoQs))
  )

  override def runInstrumented(args: List[String]): ZIO[ZEnv with Has[MetricsReporter], Nothing, ExitCode] =
    for {
      metricsReporter <- ZIO.service[MetricsReporter]
      _ <- Server
             .builder(new InetSocketAddress(bindHost, bindPort))
             .handleSome {
               case req if req.uri.getPath() == "/"      =>
                 ZIO.succeed(Response.html("<html><title>Simple Server</title><a href=\"/metrics\">Metrics</a></html>"))
               case req if req.uri.getPath == "/metrics" =>
                 ???
             }
             .serve
             .use(_.awaitShutdown)
             .fork
      _ <- putStrLn("Press Enter")
      _ <- program.fork
      _ <- getStrLn.orDie
    } yield ExitCode.success
}
```

We are serving the root path, which simply provides a link to the metrics route. The metrics route uses the instrumentation 
to produce the document and return it as HTTP response to the caller. 

## Running the prometheus example 

Any of the examples can be run from a command line within the ZMX checkout directory with 

```
sbt examples/run
```

Out of the choices, select the option corresponding to `zio.zmx.PrometheusInstrumentedApp`.

If everything works, we should be able to use a web browser to go to `http://localhost:8080/metrics` and should see something like 

```
# TYPE myCounter counter
# HELP myCounter
myCounter{effect="count2"} 46.0 1608982756235
# TYPE setGauge gauge
# HELP setGauge
setGauge 8.66004641453435 1608982756235
# TYPE changeGauge gauge
# HELP changeGauge
changeGauge 90.7178906485008 1608982756235
# TYPE myCounter counter
# HELP myCounter
myCounter{effect="count1"} 92.0 1608982756235
```

Once we see the metrics being served from our example, we can set up Prometheus and Grafana to create a simple dashboard displaying 
our metrics. 

## Simple Prometheus setup 

The following steps have been tested on Ubuntu 18.04 running inside the Windows Subsystem for Linux. Essentially, you need to 
download the prometheus binaries for your environment and start the server with our sample configuration. 

In addition, you need to download the Grafana binaries for your installation and configure prometheus as a single datasource. 

Finally, you can import our example dashboard at 'examples/prometheus/ZmxDashboard.json` and enjoy the results.

---
**NOTE**

These steps are not intended to replace the Prometheus or Grafana documentation. Please refer to their web sites for guidelines 
towards a more sophisticated setup or an installation on different platforms. 

---

### Download and configure Prometheus 

In the steps below the ZMX checkout directory will be referred to as `$ZMXDIR`.

1. [Download](https://github.com/prometheus/prometheus/releases/download/v2.23.0/prometheus-2.23.0.linux-amd64.tar.gz) prometheus
1. Extract the downloaded archive to a directory of your choice, this will be referred to as `$PROMDIR`. 
1. Within `$PROMDIR` execute 
   ```
   ./prometheus --config.file $ZMXDIR/examples/prometheus/promcfg.yml
   ```
   This will start the prometheus server which regularly polls the HTTP endpoint of the example above for its metrics.

### Download and configure Grafana

1. [Download](https://dl.grafana.com/oss/release/grafana-7.3.6.linux-amd64.tar.gz) grafana
1. Extract the downloaded archive to a directory of your choice, this will be referred to as `$GRAFANADIR`.
1. Within `$GRAFANADIR` execute 
   ```
   ./bin/grafana-server
   ```
   This will start a the Grafana server.
1. Now you should be able to login to Grafana at `http://localhost:3000' with the default user `admin` with the default 
   password `admin`. 

   Upon the first login you will be asked to change the default password. 
1. Within the Grafana menu on the left hand side you will find `Manage Dashboards` within that page, select `Import`. 
1. You can now either install a dashboard from grafana.com or use a text field to paste JSON. 
1. Paste the content of `$ZMXDIR/examples/prometheus/ZmxDashboard.json` into the text field and select `Load`.

   This will import our dashboard example. 
1. Now, under `Manage Dashboards` the just imported ZIO dashboard should be visible. 
1. Navigate to the dashboard. 

### Grafana dashboard 

Here is a screenshot of the Grafana dashboard produced with the setup above. 

![A simple Grafana Dashboard](/zio-zmx/img/ZIOZmx-Grafana.png)
