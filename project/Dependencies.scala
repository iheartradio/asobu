import sbt.Keys._
import sbt._

object Dependencies {
  object Versions {
    val play = "2.4+"
    val specs2 = "3.6.6"
    val akka = "2.4+"
  }

  val resolverSetting = resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    Resolver.bintrayRepo("scalaz", "releases"),
    Resolver.bintrayRepo("kailuowang", "maven")
  )


  val play = Seq(
    "com.typesafe.play" %% "play" % Versions.play % "provided",
    "com.typesafe.play" %% "play-json" % Versions.play % "provided",
    "com.typesafe.play" %% "play-cache" % Versions.play % "provided",
    "com.typesafe.play" %% "routes-compiler" % Versions.play
  )

  val typelevel = Seq(
    "org.typelevel" %% "cats" % "0.5.0",
    "org.typelevel" %% "kittens" % "1.0.0-M2"
  )


  val yaml = Seq(
    "org.yaml" % "snakeyaml" % "1.16"
  )

  val akka = Seq(
    "com.typesafe.akka" %% "akka-actor" % Versions.akka % "provided",
    "com.typesafe.akka" %% "akka-cluster" % Versions.akka % "provided",
    "com.typesafe.akka" %% "akka-cluster-tools" % Versions.akka % "provided",
    "com.typesafe.akka" %% "akka-cluster-metrics" % Versions.akka % "provided",
    "com.typesafe.akka" %% "akka-distributed-data-experimental" % Versions.akka,
    "com.typesafe.akka" %% "akka-testkit" % Versions.akka % "test"
  )

  val kanaloa = Seq(
    "com.iheart" %% "kanaloa" % "0.3.0"
  )

  val test = Seq(
    "com.typesafe.play" %% "play-specs2" % Versions.play % "test, provided",
    "org.specs2" %% "specs2-core" % Versions.specs2 % "test",
    "org.specs2" %% "specs2-mock" % Versions.specs2 % "test"
  )

  val simulacrum = Seq(
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    libraryDependencies += "com.github.mpilquist" %% "simulacrum" % "0.7.0"
  )

  val settings = Seq(
    libraryDependencies ++= play ++ test ++ typelevel,
    scalaVersion in ThisBuild := "2.11.7",
    resolverSetting,
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.7.1")
  ) ++ simulacrum

}
