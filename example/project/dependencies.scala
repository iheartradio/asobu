import sbt._

object Dependencies {

  object Version {
    val akka = "2.4.1"
    val play = "2.4.6"
  }

  lazy val frontend = common ++ tests ++ playSwagger
  lazy val backend = common ++ metrics ++ tests ++ play ++ playSwagger

  val play = Seq(
    "com.typesafe.play" %% "play" % Version.play
  )

  val playSwagger = Seq(
    "com.iheart" %% "play-swagger" % "0.3.1",
    "org.webjars" % "swagger-ui" % "2.1.4"
  )

  val common = Seq(
    "com.typesafe.akka" %% "akka-actor" % Version.akka,
    "com.typesafe.akka" %% "akka-cluster" % Version.akka,
    "com.typesafe.akka" %% "akka-cluster-tools" % Version.akka,
    "com.typesafe.akka" %% "akka-cluster-metrics" % Version.akka,
    "com.typesafe.akka" %% "akka-slf4j" % Version.akka,
    "com.google.guava" % "guava" % "18.0"
  )

  val metrics = Seq(
    "io.kamon" % "sigar-loader" % "1.6.6-rev002"
  )

  val tests = Seq(
    "org.scalatest" %% "scalatest" % "2.2.4" % "test",
    "org.scalatestplus" %% "play" % "1.4.0-M3" % "test",
    "com.typesafe.akka" %% "akka-testkit" % Version.akka % "test"
  )

  val kanaloa = Seq(
    "com.iheart" %% "kanaloa" % "0.3.0"
  )

}
