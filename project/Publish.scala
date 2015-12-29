import sbt._, Keys._
import bintray.BintrayKeys._


object Publish {

  val bintraySettings = Seq(
    bintrayOrganization := Some("iheartradio"),
    bintrayPackageLabels := Seq("play-framework", "DSL", "rest-api", "API", "documentation")
  )

  val publishingSettings = Seq(

    organization in ThisBuild := "com.iheart",
    publishMavenStyle := true,
    licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    homepage := Some(url("http://iheartradio.github.io/asobu")),
    scmInfo := Some(ScmInfo(url("https://github.com/iheartradio/asobu"),
      "git@github.com:iheartradio/asobu.git")),
    pomIncludeRepository := { _ => false },
    publishArtifact in Test := false
  )

  val settings = bintraySettings ++ publishingSettings
}
