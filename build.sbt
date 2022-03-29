import BuildHelper._

inThisBuild(
  List(
    organization   := "dev.zio",
    homepage       := Some(url("https://zio.github.io/zio.zmx/")),
    licenses       := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers     := List(
      Developer(
        "jdegoes",
        "John De Goes",
        "john@degoes.net",
        url("http://degoes.net"),
      ),
      Developer(
        "softinio",
        "Salar Rahmanian",
        "code@softinio.com",
        url("https://www.softinio.com"),
      ),
    ),
    pgpPassphrase  := sys.env.get("PGP_PASSWORD").map(_.toArray),
    pgpPublicRing  := file("/tmp/public.asc"),
    pgpSecretRing  := file("/tmp/secret.asc"),
    scmInfo        := Some(
      ScmInfo(url("https://github.com/zio/zio.zmx/"), "scm:git:git@github.com:zio/zio.zmx.git"),
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
  ),
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val commonSettings = Seq()

lazy val root =
  (project in file("."))
    .aggregate(coreJS, coreJVM, clientJS, clientJVM)
    .settings(
      publish / skip := true,
    )
    .enablePlugins(BuildInfoPlugin)

lazy val core =
  crossProject(JSPlatform, JVMPlatform)
    .in(file("core"))
    .settings(
      run / fork := true,
      cancelable := true,
      stdSettings("zio.zmx"),
      libraryDependencies ++= Seq(
        "dev.zio" %%% "zio"          % Version.zio,
        "dev.zio" %%% "zio-json"     % Version.zioJson,
        "dev.zio" %%% "zio-streams"  % Version.zio,
        "dev.zio"  %% "zio-test"     % Version.zio % Test,
        "dev.zio"  %% "zio-test-sbt" % Version.zio % Test,
      ),
    )
    .jvmSettings(
      libraryDependencies ++= Seq(
        "io.d11" %% "zhttp" % Version.zioHttp,
      ),
    )
    .settings(buildInfoSettings("zio.zmx"))
    .enablePlugins(BuildInfoPlugin)

lazy val coreJS  = core.js
lazy val coreJVM = core.jvm

lazy val client =
  crossProject(JSPlatform, JVMPlatform)
    .in(file("client"))
    .settings(
      commonSettings,
      crossScalaVersions := Seq(Version.Scala213),
      stdSettings("zio.zmx.client"),
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
      libraryDependencies ++= Seq(
        "dev.zio" %%% "zio"               % Version.zio,
        "dev.zio" %%% "zio-json"          % Version.zioJson,
        "dev.zio" %%% "zio-test"          % Version.zio % Test,
        "dev.zio" %%% "zio-test-magnolia" % Version.zio % Test,
        "dev.zio" %%% "zio-test-sbt"      % Version.zio % Test,
      ),
    )
    .jvmSettings(
      crossScalaVersions := Seq(Version.Scala213),
      run / fork         := true,
      run / javaOptions += "-Djava.net.preferIPv4Stack=true",
    )
    .jsSettings(
      crossScalaVersions              := Seq(Version.Scala213),
      libraryDependencies ++= Seq(
        "com.raquo"         %%% "airstream"                   % Version.airStream,
        "com.raquo"         %%% "laminar"                     % Version.laminar,
        "io.laminext"       %%% "websocket"                   % Version.laminext,
        "io.github.cquiroz" %%% "scala-java-time"             % "2.3.0",
        "org.scala-js"      %%% "scala-js-macrotask-executor" % "1.0.0",
        ("org.scala-js"      %% "scalajs-test-interface"      % scalaJSVersion % Test)
          .cross(CrossVersion.for3Use2_13),
      ),
      scalaJSLinkerConfig ~= {
        _.withModuleKind(ModuleKind.ESModule)
      },
      scalaJSLinkerConfig ~= {
        _.withSourceMap(false)
      },
      scalaJSUseMainModuleInitializer := true,
    )
    .settings(buildInfoSettings("zio.zmx.client"))
    .enablePlugins(BuildInfoPlugin)
    .dependsOn(core)

lazy val clientJS  = client.js
lazy val clientJVM = client.jvm

lazy val docs = project
  .in(file("zio-zmx-docs"))
  .settings(
    commonSettings,
    publish / skip                             := true,
    moduleName                                 := "zio.zmx-docs",
    scalacOptions -= "-Yno-imports",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"   % Version.zio,
      "io.d11"  %% "zhttp" % Version.zioHttp,
    ),
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(root),
    ScalaUnidoc / unidoc / target              := (LocalRootProject / baseDirectory).value / "website" / "static" / "api",
    cleanFiles += (ScalaUnidoc / unidoc / target).value,
    docusaurusCreateSite                       := docusaurusCreateSite.dependsOn(Compile / unidoc).value,
    docusaurusPublishGhpages                   := docusaurusPublishGhpages.dependsOn(Compile / unidoc).value,
  )
  .dependsOn(root, coreJVM)
  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)
