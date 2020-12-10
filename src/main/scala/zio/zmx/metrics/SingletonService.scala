package zio.zmx.metrics

import zio._

private[zmx] trait SingletonService[A] {

  private val (instance, sem): (Ref[Option[A]], Semaphore) = Runtime.default.unsafeRun(
    Ref.make[Option[A]](None).zip(Semaphore.make(1))
  )

  private[zmx] def makeService: ZIO[Any, Nothing, A]

  private[zmx] def service: ZIO[Any, Nothing, A] = sem.withPermit(for {
    sr  <- instance.get
    svc <- sr match {
             case Some(s) => ZIO.succeed(s)
             case None    =>
               for {
                 s <- makeService
                 _  = println(s"Created ${s.toString}")
                 _ <- instance.set(Some(s))
               } yield s
           }
  } yield (svc))
}
