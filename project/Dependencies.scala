import sbt.Keys._
import sbt._

object Dependencies {
  object Versions {
    val play = "[2.3.0, 2.4.+)"
    val specs2 = "3.6.6"
    val akka = "[2.3.0, 2.4.+)"
  }

  val play = Seq(
    "com.typesafe.play" %% "play" % Versions.play % "provided",
    "com.typesafe.play" %% "play-json" % Versions.play % "provided",
    "com.typesafe.play" %% "play-cache" % Versions.play % "provided"
  )

  val shapeless = Seq("com.chuusai" %% "shapeless" % "2.2.5")
  val cat = Seq("org.spire-math" %% "cats" % "0.3.0")

  val yaml = Seq(
    "org.yaml" % "snakeyaml" % "1.16"
  )

  val akka = Seq(
    "com.typesafe.akka" %% "akka-actor" % Versions.akka % "provided"
  )

  val test = Seq(
    "com.typesafe.play" %% "play-specs2" % Versions.play % "test, provided",
    "org.specs2" %% "specs2-core" % Versions.specs2 % "test",
    "org.specs2" %% "specs2-mock" % Versions.specs2 % "test"
  )

  val settings = Seq(
    libraryDependencies ++= play ++ test ++ shapeless ++ cat,
    scalaVersion in ThisBuild := "2.11.7",
    resolvers ++= Seq(
      Resolver.sonatypeRepo("releases"),
      Resolver.sonatypeRepo("snapshots"),
      Resolver.bintrayRepo("scalaz", "releases")
    ),
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.7.1")
  )

}
