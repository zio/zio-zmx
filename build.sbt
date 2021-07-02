import BuildHelper._

inThisBuild(
  List(
    organization := "dev.zio",
    homepage := Some(url("https://zio.github.io/zio.zmx/")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "jdegoes",
        "John De Goes",
        "john@degoes.net",
        url("http://degoes.net")
      ),
      Developer(
        "softinio",
        "Salar Rahmanian",
        "code@softinio.com",
        url("https://www.softinio.com")
      )
    ),
    pgpPassphrase := sys.env.get("PGP_PASSWORD").map(_.toArray),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc"),
    scmInfo := Some(
      ScmInfo(url("https://github.com/zio/zio.zmx/"), "scm:git:git@github.com:zio/zio.zmx.git")
    )
  )
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

val zioVersion       = "1.0.9"
val zioHttpVersion   = "1.0.0.0-RC17"
val animusVersion    = "0.1.9"
val boopickleVerison = "1.3.2"
val fansiVersion     = "0.2.14"
val laminarVersion   = "0.13.0"
val laminextVersion  = "0.13.6"

testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))

resolvers += Resolver.sonatypeRepo("snapshots")

lazy val root =
  (project in file("."))
    .aggregate(coreJS, coreJVM, clientJS, clientJVM)
    .settings(
      publish / skip := true
    )
    .enablePlugins(BuildInfoPlugin)

lazy val core =
  crossProject(JSPlatform, JVMPlatform)
    .in(file("core"))
    .settings(
      stdSettings("zio.zmx"),
      libraryDependencies ++= Seq(
        "dev.zio"     %%% "zio"          % zioVersion,
        "dev.zio"      %% "zio-nio"      % "1.0.0-RC9" % "test",
        "dev.zio"      %% "zio-test"     % zioVersion  % "test",
        "dev.zio"      %% "zio-test-sbt" % zioVersion  % "test",
        "org.polynote" %% "uzhttp"       % "0.2.7"     % "test",
        "dev.zio"      %% "zio-json"     % "0.1"       % "test"
      )
    )
    .settings(buildInfoSettings("zio.zmx"))
    .enablePlugins(BuildInfoPlugin)

lazy val coreJS  = core.js
lazy val coreJVM = core.jvm

lazy val client =
  crossProject(JSPlatform, JVMPlatform)
    .in(file("client"))
    .settings(
      stdSettings("zio.zmx.client"),
      libraryDependencies ++= Seq(
        "dev.zio"   %%% "zio"       % zioVersion,
        "io.suzaku" %%% "boopickle" % boopickleVerison
      )
    )
    .jvmSettings(
      libraryDependencies ++= Seq(
        "io.d11" %% "zhttp" % zioHttpVersion
      ),
      run / fork := true,
      run / javaOptions += "-Djava.net.preferIPv4Stack=true"
    )
    .jsSettings(
      libraryDependencies ++= Seq(
        "com.raquo"            %%% "laminar"         % laminarVersion,
        "io.github.kitlangton" %%% "animus"          % animusVersion,
        "io.laminext"          %%% "websocket"       % laminextVersion,
        "io.github.cquiroz"    %%% "scala-java-time" % "2.3.0"
      ),
      scalaJSLinkerConfig ~= {
        _.withModuleKind(ModuleKind.ESModule)
      },
      scalaJSLinkerConfig ~= {
        _.withSourceMap(false)
      },
      scalaJSUseMainModuleInitializer := true
    )
    .settings(buildInfoSettings("zio.zmx.client"))
    .enablePlugins(BuildInfoPlugin)
    .dependsOn(core)

lazy val clientJS  = client.js
lazy val clientJVM = client.jvm

lazy val examples =
  (project in file("examples"))
    .settings(
      stdSettings("zio.zmx.examples")
    )
    .settings(
      publish / skip := true,
      libraryDependencies ++= Seq(
        "dev.zio"      %% "zio"    % zioVersion,
        "org.polynote" %% "uzhttp" % "0.2.7"
      )
    )
    .dependsOn(coreJVM)

lazy val benchmarks =
  (project in file("benchmarks"))
    .settings(
      publish / skip := true
    )
    .enablePlugins(JmhPlugin)
    .dependsOn(coreJVM)

lazy val docs = project
  .in(file("zio-zmx-docs"))
  .settings(
    publish / skip := true,
    moduleName := "zio.zmx-docs",
    scalacOptions -= "-Yno-imports",
    libraryDependencies ++= Seq(
      "dev.zio"      %% "zio"    % zioVersion,
      "org.polynote" %% "uzhttp" % "0.2.7"
    ),
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(root),
    ScalaUnidoc / unidoc / target := (LocalRootProject / baseDirectory).value / "website" / "static" / "api",
    cleanFiles += (ScalaUnidoc / unidoc / target).value,
    docusaurusCreateSite := docusaurusCreateSite.dependsOn(Compile / unidoc).value,
    docusaurusPublishGhpages := docusaurusPublishGhpages.dependsOn(Compile / unidoc).value
  )
  .dependsOn(root, examples)
  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)
