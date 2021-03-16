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

val zioVersion = "1.0.5"

libraryDependencies ++= Seq(
  "dev.zio"      %% "zio"          % zioVersion,
  "dev.zio"      %% "zio-nio"      % "1.0.0-RC9" % "test",
  "dev.zio"      %% "zio-test"     % zioVersion  % "test",
  "dev.zio"      %% "zio-test-sbt" % zioVersion  % "test",
  "org.polynote" %% "uzhttp"       % "0.2.6"     % "test",
  "dev.zio"      %% "zio-json"     % "0.1"       % "test"
)

testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))

resolvers += Resolver.sonatypeRepo("snapshots")

lazy val root =
  (project in file("."))
    .settings(
      stdSettings("zio.zmx")
    )
    .settings(buildInfoSettings("zio.zmx"))
    .enablePlugins(BuildInfoPlugin)

lazy val examples =
  (project in file("examples"))
    .settings(
      stdSettings("zio.zmx")
    )
    .settings(
      skip.in(publish) := true,
      libraryDependencies ++= Seq(
        "dev.zio"      %% "zio"    % zioVersion,
        "org.polynote" %% "uzhttp" % "0.2.6"
      )
    )
    .dependsOn(root)

lazy val docs = project
  .in(file("zio-zmx-docs"))
  .settings(
    skip.in(publish) := true,
    moduleName := "zio.zmx-docs",
    scalacOptions -= "-Yno-imports",
    libraryDependencies ++= Seq(
      "dev.zio"      %% "zio"    % zioVersion,
      "org.polynote" %% "uzhttp" % "0.2.6"
    ),
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(root),
    target in (ScalaUnidoc, unidoc) := (baseDirectory in LocalRootProject).value / "website" / "static" / "api",
    cleanFiles += (target in (ScalaUnidoc, unidoc)).value,
    docusaurusCreateSite := docusaurusCreateSite.dependsOn(unidoc in Compile).value,
    docusaurusPublishGhpages := docusaurusPublishGhpages.dependsOn(unidoc in Compile).value
  )
  .dependsOn(root, examples)
  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)
