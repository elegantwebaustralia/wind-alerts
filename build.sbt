organization in ThisBuild := "com.uptech"

name in ThisBuild := "wind-alerts"
version in ThisBuild := "0.0.1-SNAPSHOT"
scalaVersion in ThisBuild := "2.12.8"


lazy val global = project
  .in(file("."))
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    domain,
    status,
    alerts
  )

lazy val domain = project
  .settings(
    name := "domain",
    settings,
    libraryDependencies ++= domainDependencies
  )
  .disablePlugins(AssemblyPlugin)

lazy val status = project
  .enablePlugins(JibPlugin)
  .settings(
    name := "status",
    settings,
    assemblySettings,
    libraryDependencies ++= statusDependencies
  )
  .dependsOn(
    domain
  )

lazy val alerts = project
  .settings(
    name := "alerts",
    settings,
    assemblySettings,
    libraryDependencies ++= alertsDependencies
  )
  .dependsOn(
    domain,
    status
  )


val Http4sVersion = "0.20.8"
val CirceVersion = "0.12.0-RC1"
val Specs2Version = "4.1.0"
val LogbackVersion = "1.2.3"

lazy val dependencies =
  new {
    val blazeServer = "org.http4s" %% "http4s-blaze-server" % Http4sVersion
    val blazeClient = "org.http4s" %% "http4s-blaze-client" % Http4sVersion
    val http4sCirce = "org.http4s" %% "http4s-circe" % Http4sVersion
    val http4sDsl = "org.http4s" %% "http4s-dsl" % Http4sVersion
    val circeGeneric = "io.circe" %% "circe-generic" % CirceVersion
    val circeParser = "io.circe" %% "circe-parser" % CirceVersion
    val circeCore = "io.circe" %% "circe-core" % CirceVersion
    val logbackClassic = "ch.qos.logback" % "logback-classic" % LogbackVersion
    val firebaseAdmin = "com.google.firebase" % "firebase-admin" % "6.9.0"
    val firebase4s = "com.github.firebase4s" %% "firebase4s" % "0.0.4"
    val sttp = "com.softwaremill.sttp" % "core_2.12" % "1.6.4"
    val specs2Core = "org.specs2" %% "specs2-core" % Specs2Version % "test"
  }

lazy val domainDependencies = Seq(
  dependencies.blazeServer,
  dependencies.blazeClient,
  dependencies.http4sCirce,
  dependencies.http4sDsl,
  dependencies.circeGeneric,
  dependencies.circeParser,
  dependencies.circeCore,
  dependencies.specs2Core,
  dependencies.logbackClassic
)

lazy val statusDependencies = domainDependencies ++ Seq(dependencies.sttp)

lazy val alertsDependencies = statusDependencies ++ Seq(dependencies.firebaseAdmin, dependencies.firebase4s)



lazy val domainSettings = Seq(
  scalacOptions ++= compilerOptions,
  resolvers ++= Seq(
    "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  )
)


lazy val settings =
  domainSettings


lazy val assemblySettings = Seq(
  assemblyJarName in assembly := name.value + ".jar",
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", xs@_*) => MergeStrategy.discard
    case "application.conf" => MergeStrategy.concat
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)

lazy val compilerOptions = Seq(
  "-unchecked",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-deprecation",
  "-encoding",
  "utf8"
)
