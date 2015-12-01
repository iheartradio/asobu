import sbt._
import Keys._

object Projects extends Build {
  lazy val playDSLAll = Project("play-dsl-all", file("."))
    .aggregate(playDSL, playDSLAkka)
    .settings(commonSettings:_*)
    .settings(noPublishing: _*)
    .settings(Testing.settings: _*)

  lazy val playDSL = Project("play-dsl", file("play-dsl-core"))
    .settings(
      commonSettings ++
      Dependencies.settings ++
      Publish.settings ++
      Format.settings ++
      Testing.settings: _*
    )

  lazy val playDSLAkka = Project("play-dsl-akka", file("play-dsl-akka"))
    .dependsOn(playDSL % "compile->compile;test->test")
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
      "-deprecation"
    )
  )

}
