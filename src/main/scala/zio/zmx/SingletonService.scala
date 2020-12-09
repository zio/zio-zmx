package zio.zmx

import zio._

private[zmx] trait SingletonService[A] {

  private val instance: Ref[Option[A]] = Runtime.default.unsafeRun(Ref.make[Option[A]](None))

  private[zmx] def makeService: ZIO[Any, Nothing, A]

  private[zmx] def service: ZIO[Any, Nothing, A] = for {
    sr  <- instance.get
    svc <- sr match {
             case Some(s) => ZIO.succeed(s)
             case None    =>
               for {
                 s <- makeService
                 _ <- instance.set(Some(s))
               } yield s
           }
  } yield (svc)
}
