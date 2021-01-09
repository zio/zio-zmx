---
id: metrics_statsd
title: "StatsD Example"
---

```scala mdoc:invisible
import zio._
import zio.console._
import zio.duration._
import zio.zmx.metrics._

import zio.zmx.statsd._
import zio.zmx.InstrumentedSample
```
## The ZMX StatsD example

For our StatsD example we will use the same instrumented code that we have used for our [Prometheus example](prometheus.md#the-zmx-prometheus-example). 

In order to run the example with statsd reporting we need to run our `program` inside a mainline that also provides a StatsD instrumentation. 

The important piece in the code below is the host and the port, which is the UDP adress of a statsd collector. Using the configuration we can create 
a StatsD instrumentation consuming all `MetricEvent`s and thereby producing the statsd datagrams to the statsd collector. 

```scala mdoc:silent
object StatsdInstrumentedApp extends ZmxApp with InstrumentedSample {

  private val config = StatsdConfig(
    host = "localhost",
    port = 8125
  )
  
  override def makeInstrumentation = StatsdInstrumentation.make(config)
  
  override def runInstrumented(args: List[String], inst: Instrumentation): URIO[ZEnv, ExitCode] = for {
    f <- program.fork
    _ <- getStrLn.catchAll(_ => ZIO.succeed(""))
    _ <- f.interrupt
  } yield (ExitCode.success)
    program
}
```

## A simple StatsD / Datadog setup 

The following steps describe how to set up a ZIO ZMX application reporting to [Datadog](https://www.datadoghq.com/) using a free Datadog account 
with limited functionality. The local setup is Windows 10 with WSL and Docker installed running Ubuntu 18.04 within WSL. 

In principle the setup is as follows:

1. The ZIO application sends datagrams to `localhost:8125` via UDP, so we need a component picking up those datagrams. 
1. Run a Datadog Collector within a docker image exposing a Unix socket for datagrams.
1. Run `socat` to listen on the UDP socket 8125 and forward incoming traffic to the Unix socket. 
1. Configure a dashboard in Datadog to visualize the metrics.

### Get and run the docker based Datadog collector 

Upon registration with dtatdoghq.com you will get an API key which is required to configure the agents collecting data. If you are planning 
to experiment with deifferent agents, take a note of your API key for further reference. In the steps below the API key will be referred to 
as `$APIKEY`


For our example we have chosen to use the docker based collector and use a unix socket to report our datagrams to that agent. 

You can start the agent from the command line with 

```
docker run --name dd-agent -e DD_API_KEY=$APIKEY -e DD_SITE=datadoghq.eu \
  -e DD_DOGSTATSD_SOCKET=/var/run/datadog/datadog.sock \
  -v /var/run/datadog:/var/run/datadog \
  -v /var/run/docker.sock:/var/run/docker.sock:ro \
  datadog/agent
```

As you can see, we require that the directory `/var/run/datadog` exists so that we can use it as a volume within the agent's docker container. The environment variable `DD_DOGSTATSD_SOCKET` tells the agent to use a unix socket to listen for datagrams. The socket file must reside within the mounted volume. 

This will start the datadog collector within docker and we have a unix socket to report our datagrams to. 

The next step is to create a UDP socket where our appilaction can report its datagrams to. For our example we have chosen `socat` to forward 
UDP traffic to our unix socket:

```
sudo socat -s -u udp-recv:8125 unix-sendto:/var/run/datadog/datadog.sock
```

---
**NOTE**

You could add the `-v` parameter to the socat call to run socat in verbose mode. That would print all traffic being forwarded to the console as well. 
This is useful to determine whether certain datagrams are actually sent. 

---

### Run the Datadog example

Now, the Datadog example can be started from within the ZMX checkout directory with 

```
sbt examples/run
```

Select the entry corresponding to `zio.zmx.StatsdInstrumentedApp`. If you have started socat in verbose mode you should see traffic going through socat 
to the stats collector. 

### Visualize the metrics

1. Log in to your datadog account 
1. From the menu on left hand side select `Dashboards/New Dashboard'
1. In the upper right corner, click on the dashboard settings and select 'Import Dashboard JSON' 
1. From the filesystem, select `$ZMXDIR/examples/statsd/ZIOZMXmetrics.json`
1. Confirm to override the dashboard configuration 
1. Save the just imported dashboard 
1. From the dashboard list select _ZIO ZMX metrics_
1. The Datadog dashboard is displayed

### Datadog dashboard 

![A simple Datadog Dashboard](/zio-zmx/img/ZIOZmx-Datadog.png)



