package zio.zmx.client.backend

import scala.util.Try

import zio._
import zio.stream._

import uzhttp.websocket._

import upickle.default._

import zio.zmx.client.ClientMessage
import zio.zmx.notify.MetricNotifier
import zio.zmx.client.ClientMessage.Disconnect
import zio.zmx.client.ClientMessage.Subscription
import zio.zmx.client.ClientMessage.RemoveSubscription

trait WSHandler {
  def handleZMXFrame(input: Frame): UIO[Stream[Nothing, Take[Nothing, Frame]]]
}

object WSHandler {
  val live: ZLayer[Clock with MetricNotifier, Nothing, WSHandler] = (for {
    clk      <- ZIO.service[Clock]
    notifier <- ZIO.service[MetricNotifier]
  } yield WSHandlerImpl(clk, notifier)).toLayer

  case class WSHandlerImpl(
    clk: Clock,
    notifier: MetricNotifier
  ) extends WSHandler {

    def handleZMXFrame(frame: Frame): UIO[Stream[Nothing, Take[Nothing, Frame]]] =
      for {
        cltMsg <- toClientMessage(frame).map(Some(_)).catchAll(_ => ZIO.none)
        _      <- ZIO.logInfo(s"Got message from client : <$cltMsg>")
        str    <- cltMsg match {
                    case None                        => ZIO.succeed(Stream.empty)
                    // The connect message requires a special treatment since it sets up the stream of
                    // metric updates sent to the client
                    case Some(ClientMessage.Connect) =>
                      ZIO.succeed(connect().map(m => Take.single(Binary(write(m).getBytes()))))
                    // All other messages work within the context of an existing Client connection
                    case Some(msg)                   =>
                      handleMessage(msg)
                  }
      } yield str

    private def toClientMessage(frame: Frame): ZIO[Any, Any, ClientMessage] = frame match {
      case Binary(data, _)       => ZIO.fromTry(Try(read[ClientMessage](new String(data))))
      case Text(data, _)         => ZIO.fromTry(Try(read[ClientMessage](data)))
      case Continuation(data, _) => ZIO.fromTry(Try(read[ClientMessage](new String(data))))
      case _                     => ZIO.fail(())
    }

    private def handleMessage(msg: ClientMessage): UIO[Stream[Nothing, Take[Nothing, Frame]]] = {
      val effect =
        msg match {
          case Disconnect(cltId)                     => notifier.disconnect(cltId)
          case Subscription(clt, id, keys, interval) => notifier.subscribe(clt, id, Chunk.fromIterable(keys), interval)
          case RemoveSubscription(clt, id)           => notifier.unsubscribe(clt, id)
          case _                                     => ZIO.unit
        }
      effect.as(Stream(Take.single(Close), Take.end))
    }

    private def connect(): UStream[ClientMessage] =
      (ZStream
        .fromZIO(for {
          connected <- notifier.connect()
          conMsg     = ZStream.from(ClientMessage.Connected(connected._1))
          metrics    = connected._2
                         .map(st => ClientMessage.MetricsNotification(st.clt, st.subId, st.when, st.states))
                         .tap(msg => ZIO.logInfo(s"Sending update to client : <$msg>"))
          keys       = connected._3.map(k => ClientMessage.AvailableMetrics(k))

        } yield conMsg ++ (metrics.merge(keys))))
        .flatten
  }
}
