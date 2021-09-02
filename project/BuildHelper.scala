import sbt._
import sbt.Keys._
import sbtbuildinfo._
import BuildInfoKeys._

object BuildHelper {
  val Scala211   = "2.11.12"
  val Scala212   = "2.12.14"
  val Scala213   = "2.13.6"
  val ScalaDotty = "3.0.2"

  private val stdOptions = Seq(
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:higherKinds",
    "-language:existentials",
    "-unchecked",
    "-deprecation"
    //"-Xfatal-warnings"
  )

  private val stdOpts2X = Seq(
    "-explaintypes",
    "-Yrangepos",
    "-Xlint:_,-type-parameter-shadow",
    "-Xsource:2.13",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard"
  )

  private val stdOpts213 = Seq(
    "-Wunused:imports",
    "-Wvalue-discard",
    "-Wunused:patvars",
    "-Wunused:privates",
    //"-Wunused:params",
    "-Wvalue-discard"
  )

  private val stdOptsUpto212 = Seq(
    "-Xfuture",
    "-Ypartial-unification",
    "-Ywarn-nullary-override",
    "-Yno-adapted-args",
    "-Ywarn-infer-any",
    "-Ywarn-inaccessible",
    "-Ywarn-nullary-unit",
    "-Ywarn-unused-import"
  )

  private val dottyOptions = Seq(
    "-noindent",
    "-source:3.0-migration",
    "-Xignore-scala2-macros"
  )

  private def silencerVersion(scalaVersion: String) = scalaVersion match {
    case "2.12.14" => "1.7.5"

    case "2.13.1" => "1.6.0"
    case "2.13.2" => "1.6.0"
    case "2.13.4" => "1.7.3"
    case "2.13.6" => "1.7.5"
    case _        => "1.7.1"
  }

  private def extraOptions(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((3, 0))  => dottyOptions
      case Some((2, 13)) =>
        stdOpts213 ++ stdOpts2X
      case Some((2, 12)) =>
        Seq(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused:_,imports",
          "-Ywarn-unused:imports",
          "-opt:l:inline",
          "-opt-inline-from:<source>"
        ) ++ stdOptsUpto212 ++ stdOpts2X
      case _             =>
        Seq("-Xexperimental") ++ stdOptsUpto212 ++ stdOpts2X
    }

  def buildInfoSettings(packageName: String) =
    Seq(
      buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, isSnapshot),
      buildInfoPackage := packageName,
      buildInfoObject := "BuildInfo"
    )

  def stdSettings(prjName: String) =
    Seq(
      name := s"$prjName",
      crossScalaVersions := Seq(Scala212, Scala213, ScalaDotty),
      ThisBuild / scalaVersion := Scala213,
      scalacOptions := stdOptions ++ extraOptions(scalaVersion.value),
      libraryDependencies ++= {
        if (scalaVersion.value != ScalaDotty)
          Seq(
            ("com.github.ghik"   % "silencer-lib"    % silencerVersion(scalaVersion.value) % Provided)
              .cross(CrossVersion.full),
            compilerPlugin(
              ("com.github.ghik" % "silencer-plugin" % silencerVersion(scalaVersion.value)).cross(CrossVersion.full)
            )
          )
        else
          Seq()
      },
      incOptions ~= (_.withLogRecompileOnMacro(false)),
      Test / parallelExecution := false
    )
}
