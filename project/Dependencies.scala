import sbt.Keys._
import sbt._

object Dependencies {
  object Versions {
    val play = "2.4.2"
    val specs2 = "3.0"
  }

  val play = Seq(
    "com.typesafe.play" %% "play" % Versions.play % "provided",
    "com.typesafe.play" %% "play-json" % Versions.play % "provided"
  )

  val shapeless = Seq("com.chuusai" %% "shapeless" % "2.2.5")

  val yaml = Seq(
    "org.yaml" % "snakeyaml" % "1.16"
  )

  val test = Seq(
    "org.specs2" %% "specs2-core" % Versions.specs2 % "test",
    "org.specs2" %% "specs2-mock" % Versions.specs2 % "test"
  )

  val settings = Seq(
    libraryDependencies ++= play ++ test ++ shapeless,
    scalaVersion in ThisBuild := "2.11.7",
    resolvers ++= Seq(
      Resolver.sonatypeRepo("releases"),
      Resolver.sonatypeRepo("snapshots"),
      Resolver.bintrayRepo("scalaz", "releases")
    )
  )

}
