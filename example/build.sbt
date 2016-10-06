import NativePackagerHelper._
import com.typesafe.sbt.SbtGit.GitKeys.gitHeadCommit


val resolverSetting = resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  Resolver.bintrayRepo("scalaz", "releases"),
  Resolver.jcenterRepo
)


val commonSettings = Seq(
  organization := "asobu",
  version := "2.4.3",
  scalaVersion := "2.11.8",
  addCompilerPlugin("com.milessabin" % "si2712fix-plugin" % "1.2.0" cross CrossVersion.full),
  // build info
  buildInfoPackage := "meta",
  buildInfoOptions ++= Seq(BuildInfoOption.ToJson),
  buildInfoKeys := Seq[BuildInfoKey](
    name, version, scalaVersion
  )++ gitHeadCommit.value.map {
    value ⇒ BuildInfoKey("gitCommit" → value)
  }.toSeq,
  resolverSetting
)

lazy val example = (project in file("."))
  .settings(
    name := "asobu-example"
  )
  .aggregate(api, frontend, backend)
  
lazy val frontend = (project in file("frontend"))
  .dependsOn(asobuDSL)
  .dependsOn(asobuDistributed)
  .dependsOn(asobuDistributedKanaloa)
  .dependsOn(asobuDSLAkka)
  .enablePlugins(PlayScala, BuildInfoPlugin, JavaAppPackaging)
    .settings(
        name := "cluster-play-frontend",
        libraryDependencies ++= (Dependencies.frontend  ++ Dependencies.kanaloa ++ Seq(filters, cache)),
        routesGenerator := InjectedRoutesGenerator,
        fork in run := true,
        commonSettings
    ).dependsOn(api)

lazy val backend = (project in file("backend"))
  .dependsOn(asobuDSL)
  .dependsOn(asobuDistributed)
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging)
    .settings(
        name := "cluster-akka-backend",
        libraryDependencies ++= Dependencies.backend,
        fork in run := true,
        commonSettings
    ).dependsOn(api)
    
lazy val api = (project in file("api"))
  .dependsOn(asobuDistributed)
  .enablePlugins(BuildInfoPlugin)
  .settings(
        name := "cluster-api",
        libraryDependencies ++= Dependencies.backend,
        commonSettings
    )

lazy val asobuDSL = ProjectRef(file("../"), "asobu-dsl")
lazy val asobuDistributed = ProjectRef(file("../"), "asobu-distributed")
lazy val asobuDistributedKanaloa = ProjectRef(file("../"), "asobu-distributed-kanaloa")
lazy val asobuDSLAkka = ProjectRef(file("../"), "asobu-dsl-akka")

//
// Scala Compiler Options
// If this project is only a subproject, add these to a common project setting.
//
scalacOptions in ThisBuild ++= Seq(
  "-target:jvm-1.8",
  "-encoding", "UTF-8",
  "-deprecation", // warning and location for usages of deprecated APIs
  "-feature", // warning and location for usages of features that should be imported explicitly
  "-unchecked", // additional warnings where generated code depends on assumptions
  "-Xlint", // recommended additional warnings
  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver
  "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
  "-Ywarn-inaccessible",
  "-Ywarn-dead-code"
)

