import sbt._
import Keys._

object Projects extends Build {
  lazy val root = Project("root", file("."))
    .aggregate(dsl, dslAkka)
    .settings(commonSettings:_*)
    .settings(noPublishing: _*)
    .settings(Testing.settings: _*)

  lazy val dsl = Project("asobu-dsl", file("dsl"))
    .settings(
      commonSettings ++
      Dependencies.settings ++
      Publish.settings ++
      Format.settings ++
      Testing.settings: _*
    )

  lazy val dslAkka = Project("asobu-dsl-akka", file("dsl-akka"))
    .dependsOn(dsl)
    .aggregate(dsl)
    .settings(
      commonSettings ++
      Dependencies.settings ++
      Publish.settings ++
      Format.settings ++
      Testing.settings: _*)
      .settings(
        libraryDependencies ++= Dependencies.akka
      )

  val noPublishing = Seq(publish := (), publishLocal := (), publishArtifact := false)

  val commonSettings = Seq(
    scalacOptions ++= Seq(
      "-deprecation",
      "-unchecked",
      "-Xlint"
    )
  )

}
