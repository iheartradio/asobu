package asobu.distributed.service

import java.io.File

import asobu.distributed.EndpointDefinition
import asobu.distributed.gateway.Endpoint.Prefix
import play.routes.compiler.{Route, RoutesCompilationError, RoutesFileParser}

import scala.io.Source

object EndpointDefinitionParser {

  /**
   * for testing purpose only
   * @param prefix
   * @param content
   * @param createEndpointDef
   * @return
   */
  private[distributed] def parse(
    prefix: Prefix,
    content: String,
    createEndpointDef: (Route, Prefix) ⇒ EndpointDefinition
  ): Either[Seq[RoutesCompilationError], List[EndpointDefinition]] = {
    parseContent(content, "remote-routes").right.map(_.map(createEndpointDef(_, prefix)))
  }

  //to conform to play api
  private def placeHolderFile(resourceName: String) = new File(resourceName)

  def parseResource(
    resourceName: String = "remote.routes"
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

  def parseContent(
    content: String,
    resourceName: String
  ): Either[Seq[RoutesCompilationError], List[Route]] = {
    import cats.std.either._
    import cats.std.list._
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
