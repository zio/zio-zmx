package zio.zmx.client.backend

import scala.util.Try

import zio._
import zio.stream._

import uzhttp.websocket._

import upickle.default._

import zio.zmx.client.ClientMessage
import zio.zmx.notify.MetricNotifier
import zio.zmx.client.MetricsUpdate

trait WSHandler {
  def handleZMXSocket(input: Stream[Throwable, Frame]): Stream[Throwable, Frame]
}

object WSHandler {
  val live: ZLayer[MetricNotifier, Nothing, WSHandler] = (for {
    buf      <- Queue.bounded[ClientMessage](64)
    notifier <- ZIO.service[MetricNotifier]
    daemon   <- Ref.Synchronized.make[Option[Fiber.Runtime[_, _]]](None)
  } yield WSHandlerImpl(notifier, buf, daemon)).toManagedWith(_.stop()).toLayer

  case class WSHandlerImpl(
    notifier: MetricNotifier,
    cmdBuf: Queue[ClientMessage],
    daemon: Ref.Synchronized[Option[Fiber.Runtime[_, _]]]
  ) extends WSHandler {
    def handleZMXSocket(input: Stream[Throwable, Frame]): Stream[Throwable, Frame] =
      appSocket(input)

    private def cmdReader(input: Stream[Throwable, Frame]): ZIO[ZEnv, Nothing, Fiber.Runtime[_, _]] =
      ZIO.logInfo(s"Starting Command Reader ...") *>
        input.map {
          case Text(s, _)   =>
            println(s)
            Try(read[ClientMessage](s)).toOption
          case Binary(d, _) =>
            val s = new String(d)
            println(s)
            Try(read[ClientMessage](s)).toOption
          case o            =>
            println(o)
            None
        }.collect { case Some(m) => m }.foreach { msg =>
          ZIO.logInfo(s"Received Client Message <$msg>") *>
            cmdBuf.offer(msg)
        }.forkDaemon

    private lazy val appSocket: Stream[Throwable, Frame] => Stream[Throwable, Frame] = input => {

      val handleMessage: ClientMessage => UStream[ClientMessage] = {
        case ClientMessage.Connect =>
          (ZStream
            .fromZIO(for {
              connected <- notifier.connect()
              states     = ZStream.from(ClientMessage.Connected(connected._1)) ++ connected._2.map(st =>
                             ClientMessage.MetricsNotification(Chunk.fromIterable(st.map { case (k, s) =>
                               MetricsUpdate.fromMetricState(k, s)
                             }))
                           )
            } yield states))
            .flatten
        case _                     =>
          Stream.empty
      }

      Runtime.default.unsafeRun(
        daemon.updateSomeZIO { case None =>
          cmdReader(input).map(Some(_))
        }
      )

      ZStream
        .fromQueue(cmdBuf)
        .flatMap(msg => handleMessage(msg).takeUntilZIO(_ => cmdBuf.size.map(_ > 0)))
        .map(msg => Text(write(msg)))
      // ZStream
      //   .fromQueue(cmdBuf)
      //   .map { case ClientMessage.Connect =>
      //     ZIO.succeed(Stream.empty)
      //   // for {
      //   //   connected <- notifier.connect()
      //   //   states     = connected._2
      //   //   idMsg      = Text(write(ClientMessage.Connected(connected._1)))
      //   //   frames     =
      //   //     states.map { st =>
      //   //       val cltMsg =
      //   //         ClientMessage.MetricsNotification(Chunk.fromIterable(st.map { case (k, s) =>
      //   //           MetricsUpdate.fromMetricState(k, s)
      //   //         }))
      //   //       Text(write(cltMsg))
      //   //     }
      //   // } yield ZStream(idMsg) ++ frames.takeUntilZIO(_ => cmdBuf.size.map(_ > 0))

      //   //     case o                     =>
      //   //       ZIO.logDebug(s"$o") *> ZIO.succeed(Stream.empty)
      //   //   }
      //   }
      //   .flatten
    }

    private[WSHandler] def stop() = daemon.updateSomeZIO { case Some(d) =>
      (cmdBuf.shutdown *> d.interrupt).as(None)
    }

  }

}
