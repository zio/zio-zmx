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
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

resolvers += Resolver.sonatypeRepo("snapshots")

lazy val root =
  (project in file("."))
    .aggregate(coreJS, coreJVM, clientJS, clientJVM, examples)
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
        "dev.zio" %%% "zio"          % Version.zio,
        "dev.zio" %%% "zio-streams"  % Version.zio,
        "dev.zio"  %% "zio-test"     % Version.zio % Test,
        "dev.zio"  %% "zio-test-sbt" % Version.zio % Test
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
      crossScalaVersions := Seq(Version.Scala213, Version.ScalaDotty),
      stdSettings("zio.zmx.client"),
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
      libraryDependencies ++= Seq(
        "dev.zio"     %%% "zio"          % Version.zio,
        "com.lihaoyi" %%% "upickle"      % Version.upickle,
        "dev.zio"     %%% "zio-test"     % Version.zio % Test,
        "dev.zio"     %%% "zio-test-sbt" % Version.zio % Test
      )
    )
    .jvmSettings(
      crossScalaVersions := Seq(Version.Scala213, Version.ScalaDotty),
      libraryDependencies ++= Seq(
        "dev.zio"      %% "zio"       % Version.zio,
        "io.netty"      % "netty-all" % "4.1.73.Final",
        "org.polynote" %% "uzhttp"    % Version.uzhttp
      ),
      run / fork := true,
      run / javaOptions += "-Djava.net.preferIPv4Stack=true"
    )
    .jsSettings(
      crossScalaVersions := Seq(Version.Scala213, Version.ScalaDotty),
      libraryDependencies ++= Seq(
        "com.raquo"         %%% "airstream"                   % Version.airStream,
        "com.raquo"         %%% "laminar"                     % Version.laminar,
        "io.laminext"       %%% "websocket"                   % Version.laminext,
        "io.github.cquiroz" %%% "scala-java-time"             % "2.3.0",
        "org.scala-js"      %%% "scala-js-macrotask-executor" % "1.0.0",
        ("org.scala-js"      %% "scalajs-test-interface"      % scalaJSVersion % Test)
          .cross(CrossVersion.for3Use2_13)
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
        "dev.zio"      %% "zio"    % Version.zio,
        "org.polynote" %% "uzhttp" % Version.uzhttp
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
      "dev.zio"      %% "zio"    % Version.zio,
      "org.polynote" %% "uzhttp" % Version.uzhttp
    ),
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(root),
    ScalaUnidoc / unidoc / target := (LocalRootProject / baseDirectory).value / "website" / "static" / "api",
    cleanFiles += (ScalaUnidoc / unidoc / target).value,
    docusaurusCreateSite := docusaurusCreateSite.dependsOn(Compile / unidoc).value,
    docusaurusPublishGhpages := docusaurusPublishGhpages.dependsOn(Compile / unidoc).value
  )
  .dependsOn(root, examples)
  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)
