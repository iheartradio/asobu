import org.scoverage.coveralls.Imports.CoverallsKeys._
import sbt._
import sbt.Keys._

object Testing {


  lazy val settings = Seq(
      coverallsToken := Some("8IGVvRI0URTzmzUd5Ts7CtsMecw6wbkme"),
      scalacOptions in Test ++= Seq("-Yrangepos"),
      testOptions in Test := Seq(Tests.Argument(TestFrameworks.Specs2, "-xonly"))
    )


}
