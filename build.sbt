
lazy val root = Project("root", file("."))
  .aggregate(dsl, dslAkka, distributed, distributedKanaloa)
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

lazy val distributed = Project("asobu-distributed", file("distributed"))
  .dependsOn(dsl)
  .aggregate(dsl)
  .settings(
    commonSettings ++
      Dependencies.settings ++
      Publish.settings ++
      Format.settings ++
      Testing.settings: _*)
  .settings(
    libraryDependencies ++= Dependencies.akka ++ Seq(
      "com.typesafe.akka" %% "akka-agent" % Dependencies.Versions.akka
    )
  )

lazy val distributedKanaloa = Project("asobu-distributed-kanaloa", file("distributed-kanaloa"))
  .dependsOn(distributed)
  .aggregate(distributed)
  .settings(
    commonSettings ++
      Dependencies.settings ++
      Publish.settings ++
      Format.settings ++
      Testing.settings: _*)
  .settings(
    libraryDependencies ++= Dependencies.akka ++ Dependencies.kanaloa
  )

val noPublishing = Seq(publish := (), publishLocal := (), publishArtifact := false)

val commonSettings = Seq(
  scalaVersion := "2.11.8",
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-unchecked",
    "-Xlint"
  )
)
