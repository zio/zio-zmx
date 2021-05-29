package zio.zmx.client.backend

import boopickle.Default._
import io.netty.buffer.Unpooled
import zhttp.core.ByteBuf
import zhttp.http._
import zhttp.service._
import zhttp.socket.{ Socket, WebSocketFrame }
import zio._
import zio.stream.{ UStream, ZStream }
import zio.zmx.client.MetricsMessage
import zio.zmx.internal.{ MetricKey, MetricListener }
import zio.zmx.state.MetricState

import java.time.Duration

trait MetricsProtocol {
  val statsStream: UStream[MetricsMessage]
}

object MetricsProtocol {
  def live: ZLayer[Any, Nothing, Has[MetricsProtocol]] = {
    for {
      hub     <- Hub.sliding[MetricsMessage](4096).toManaged_
      listener = hubListener(hub)
      _       <- ZManaged.makeEffectTotal_(zmx.internal.installListener(listener))(zmx.internal.removeListener(listener))
    } yield new MetricsProtocol {
      override val statsStream: UStream[MetricsMessage] =
        ZStream.fromHub(hub)
    }
  }.toLayer

  // Accessors

  val statsStream: ZStream[Has[MetricsProtocol], Nothing, MetricsMessage] =
    ZStream.accessStream[Has[MetricsProtocol]](_.get.statsStream)

  private def hubListener(hub: Hub[MetricsMessage]): MetricListener = new MetricListener {
    override def gaugeChanged(key: MetricKey.Gauge, value: Double, delta: Double): UIO[Unit] =
      hub.publish(MetricsMessage.GaugeChange(key, value, delta)).unit

    override def counterChanged(key: MetricKey.Counter, absValue: Double, delta: Double): UIO[Unit] =
      hub.publish(MetricsMessage.CounterChange(key, absValue, delta)).unit

    override def histogramChanged(key: MetricKey.Histogram, value: MetricState): UIO[Unit] =
      hub.publish(MetricsMessage.HistogramChange(key, value)).unit

    override def summaryChanged(key: MetricKey.Summary, value: MetricState): UIO[Unit] =
      hub.publish(MetricsMessage.SummaryChange(key, value)).unit

    override def setChanged(key: MetricKey.SetCount, value: MetricState): UIO[Unit] =
      hub.publish(MetricsMessage.SetChange(key, value)).unit
  }

}

//case class MetricsProtocolLive() extends MetricsProtocol {
//  override val statsStream: UStream[MetricState] = ???
//}

object MetricsServer extends App {
  implicit val durationPickler: Pickler[Duration] =
    transformPickler((long: Long) => Duration.ofMillis(long))(_.toMillis)

  val appSocket = Socket.collect[WebSocketFrame] { case WebSocketFrame.Text("SUBSCRIBE") =>
    MetricsProtocol.statsStream.map { state =>
      println(s"BROADCASTING $state")
      val byteBuf = Unpooled.wrappedBuffer(Pickle.intoBytes(state))
      WebSocketFrame.binary(ByteBuf(byteBuf))
    }
  }

  val app =
    HttpApp.collect { case Method.GET -> Root / "ws" =>
      Response.socket(appSocket)
    }

  val program = for {
    _ <- UIO(println("STARTING SERVER"))
    _ <- Server.start(8089, app)
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    program
      .provideCustomLayer(MetricsProtocol.live)
      .exitCode
}
