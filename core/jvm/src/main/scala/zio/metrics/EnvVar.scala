package zio.metrics.connectors

import java.time

import zio._

final case class EnvVar[A](envVar: String, requiredBy: String)(cm: String => A) {

  def getWithDefault(default: A): ZIO[Any, Nothing, A] =
    ZIO
      .fromOption(Option(java.lang.System.getenv(envVar)))
      .flatMap(envVar => ZIO.attempt(cm(envVar)))
      .catchAll(_ => ZIO.succeed(default))

  def get: ZIO[Any, IllegalArgumentException, A] =
    ZIO
      .fromOption(Option(java.lang.System.getenv(envVar)))
      .flatMap(envVar => ZIO.attempt(cm(envVar)))
      .mapError { case e =>
        new IllegalArgumentException(
          s"The environment variable, `$envVar`, is required by '$requiredBy'.  Root cause: $e",
        )
      }

  def contramap[B](f: A => B): EnvVar[B] =
    copy()(cm andThen f)

  def isDefined: Boolean = java.lang.System.getenv(envVar) != null

}

object EnvVar {

  def string(envVar: String, requiredBy: String): EnvVar[String] =
    EnvVar(envVar, requiredBy)(identity)

  def boolean(envVar: String, requiredBy: String): EnvVar[Boolean] =
    string(envVar, requiredBy).contramap(_.toBoolean)

  def duration(envVar: String, requiredBy: String): EnvVar[Duration] =
    string(envVar, requiredBy).contramap(time.Duration.parse)

  def int(envVar: String, requiredBy: String): EnvVar[Int] =
    string(envVar, requiredBy).contramap(_.toInt)
}
