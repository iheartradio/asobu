import NativePackagerHelper._

val resolverSetting = resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  Resolver.bintrayRepo("scalaz", "releases"),
  Resolver.jcenterRepo
)


val commonSettings = Seq(
  organization := "asobu",
  version := "2.4.3",
  scalaVersion := "2.11.7",

  // build info
  buildInfoPackage := "meta",
  buildInfoOptions ++= Seq(BuildInfoOption.ToJson,
                          BuildInfoOption.Traits("asobu.distributed.service.BuildNumber")),
  buildInfoKeys := Seq[BuildInfoKey](
    name, version, scalaVersion, buildInfoBuildNumber
  ),
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
  .dependsOn(asobuDSLAkka)
  .enablePlugins(PlayScala, BuildInfoPlugin, JavaAppPackaging)
    .settings(
        name := "cluster-play-frontend",
        libraryDependencies ++= (Dependencies.frontend  ++ Dependencies.kanaloa ++ Seq(filters, cache)),
        routesGenerator := InjectedRoutesGenerator,
        javaOptions ++= Seq(
            "-Djava.library.path=" + (baseDirectory.value.getParentFile / "backend" / "sigar" ).getAbsolutePath,
            "-Xms128m", "-Xmx1024m"),
        fork in run := true,
        mappings in Universal ++= directory(baseDirectory.value.getParentFile / "backend" / "sigar"),
        bashScriptExtraDefines ++= Seq(
          """declare -r sigar_dir="$(realpath "${app_home}/../sigar")"""",
          """addJava "-Djava.library.path=${sigar_dir}""""
        ),
        commonSettings
    ).dependsOn(api)

lazy val backend = (project in file("backend"))
  .dependsOn(asobuDSL)
  .dependsOn(asobuDistributed)
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging)
    .settings(
        name := "cluster-akka-backend",
        libraryDependencies ++= Dependencies.backend,
        javaOptions ++= Seq(
            "-Djava.library.path=" + (baseDirectory.value / "sigar").getAbsolutePath,
            "-Xms128m", "-Xmx1024m"),
        // this enables custom javaOptions
        fork in run := true,
        mappings in Universal ++= directory(baseDirectory.value / "sigar"),
        bashScriptExtraDefines ++= Seq(
          """declare -r sigar_dir="$(realpath "${app_home}/../sigar")"""",
          """addJava "-Djava.library.path=${sigar_dir}""""
        ),
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

