package asobu.distributed.service

import java.io.File
import asobu.distributed.protocol.Prefix
import asobu.distributed.protocol.EndpointDefinition
import play.routes.compiler.{Route, RoutesCompilationError, RoutesFileParser}

import scala.io.Source

object EndpointRoutesParser {

  val defaultResourceName = "remote.routes"

  //to conform to play api
  private def placeHolderFile(resourceName: String) = new File(resourceName)

  def parseResource(
    resourceName: String = defaultResourceName
  ): Either[Seq[RoutesCompilationError], List[Route]] = {
    def routesFileNotFound = RoutesCompilationError(
      placeHolderFile(resourceName),
      s"$resourceName doesn't exsit in resources.", None, None
    )

    Option(getClass.getClassLoader.getResourceAsStream(resourceName))
      .toRight(List(routesFileNotFound))
      .right.flatMap { inputStream ⇒
        val content = Source.fromInputStream(inputStream).mkString
        parseContent(content, resourceName)
      }
  }

  private[distributed] def parseContent(
    content: String,
    resourceName: String = defaultResourceName
  ): Either[Seq[RoutesCompilationError], List[Route]] = {
    import cats.instances.either._
    import cats.instances.list._
    import cats.syntax.traverse._

    val phf = placeHolderFile(resourceName) //to conform to play api

    lazy val unsupportedError = Seq(RoutesCompilationError(phf, "doesn't support anything but route", None, None))
    RoutesFileParser.parseContent(content, phf).right.flatMap { routes ⇒
      routes.traverse[Either[Seq[RoutesCompilationError], ?], Route] {
        case r: Route ⇒ Right(r)
        case _        ⇒ Left(unsupportedError)
      }
    }
  }
}
