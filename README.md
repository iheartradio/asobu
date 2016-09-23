[![Build Status](https://travis-ci.org/iheartradio/asobu.svg)](https://travis-ci.org/iheartradio/asobu)
[![Stories in Ready](https://badge.waffle.io/iheartradio/asobu.svg?label=ready&title=Ready)](http://waffle.io/iheartradio/asobu)
[![Codacy Badge](https://api.codacy.com/project/badge/coverage/a5d2ce7f42234a0f8091b1add11696a6)](https://www.codacy.com/app/kailuo-wang/asobu)
[![Codacy Badge](https://api.codacy.com/project/badge/grade/a5d2ce7f42234a0f8091b1add11696a6)](https://www.codacy.com/app/kailuo-wang/asobu)


# Asobu

## Note: this libary is not ready for external usage outside of iHeartRadio yet. 

Asobu is a library that provides ability to create distributed Http endpoints between Play applications and Akka applications deployed in an Akka cluster.

## Caveat
Asobu requires use site having [SI-2712](https://issues.scala-lang.org/browse/SI-2712) fixed. The easiest way to achieve that would be using Miles Sabing's [SI-2712 fix plugin](https://github.com/milessabin/si2712fix-plugin). In short, you can add the following to your build.sbt file. 
Also we are pre-release so the API is by no means stable yet, but we'd love to see feedbacks, contributions etc. 

```Scala
addCompilerPlugin("com.milessabin" % "si2712fix-plugin" % "1.1.0" cross CrossVersion.full)
```

## Distributed Endpoints

Distributed endpoints allows you to dynamically add/remove Http endpoints at the Play app and having the request preprocessed and forwarded to the Akka App. This will allow a more flexible microservice architecture in which

1. the play app serves http endpoints as an API gateway
2. microservices are implemented as Akka apps and can add/remove Http endpoints at the API gateway
3. there is no binary dependencies between the API gateway and the microservics.
4. complex cross-cutting logic such as authentication, validation and error handling can be written in the central API gateway, i.e. the play app, and updated without redeploying the microservices.

## Usage

Add asobu to dependences in your play app as

```Scala
libraryDependencies ++= {
  val version = "0.1.3"
  Seq(
    "com.iheart" %% "asobu-dsl" % version,
    "com.iheart" %% "asobu-distributed" % version,
    "com.iheart" %% "asobu-dsl-akka" % version
  )
}

```
Add asobu to dependencies in your akka app as

```Scala
libraryDependencies ++= {
  val version = "0.1.3"
  Seq(
    "com.iheart" %% "asobu-dsl" % version,
    "com.iheart" %% "asobu-distributed" % version,
    "com.iheart" %% "asobu-distributed-kanaloa" % version,
    "com.iheart" %% "asobu-dsl-akka" % version
  )
}

```

Then for both the play app and the akka app you have to enable distributed data by adding the following into the conf file,

```
akka {

  //add distributed data extension to existing extensions
  extensions += "akka.cluster.ddata.DistributedData"

  //add the cluster role to existing roles
  cluster.roles += "distributed-service"

  actor.distributed-data.role = "distributed-service"

}

```

Then to create new endpoints at the akka app side, you need to write routes files and controllers.
The routes file will look like a play routes file
Here is example

```
GET   /users/:userId/tests/groups    abtest.MainController.getGroups(userId: Int, time: Long)

POST  /tests      poweramp.abtest.MainController.createTest()
```

Then write a controller using asobu DSL, e.g.
```scala
package abtest

case class MainController(backend: ActorRef)(
  implicit
  sys:     ActorSystem,
  epc:     EndpointsRegistryClient,
  timeout: Timeout,
  ec:      ExecutionContext
) extends DistributedController {

  handle(
    "getGroups",
    process[GetGroups]()
  )(using(backend).
      expect[GroupsResult] >>
      respondJson(Ok))

  handle(
    "createTest",
    process[CreateTest](
     from(author = authenticatedUserName),
     from(test   = jsonBody[ABTest]))
  )(using(backend).
      expect[TestCreated] >>
      respondJson(Ok))
}

```

The constructor parameter `backend` is the actual actor that handles the messages such as `GetGroups` and `CreateTest` in this example.
Then in the main class of the akka app you need to initialize the controllers so they get to register the endpoints they created to the remote play app.
```
  implicit val timeout: Timeout = 30.seconds

    init { implicit rec ⇒
      List(
        new MainController(myBackend)
      )
    }

```

finally some boilerplate on the play app side.
first in the conf file you need to enable asobu module
```
play.modules.enabled += "asobu.distributed.gateway.GateWayModule"
```

then at the last of your root routes file add
```
->    /                         asobu.distributed.gateway.GatewayRouter
```

Then you should be all set.


## Example

An example project can be find in the [example](/example) folder.

## Swagger integration

Asobu supports API documentation generation integration, so that API documentation can be generated at individual microservices (i.e. akka app) and submitted to the play app to be congregated as a whole. You can find an example of such integration with 
[play-swagger](https://github.com/iheartradio/play-swagger) in the example. The basic idea is that you provide an apiDocGenerator at the Akka microservice side before initing the controllers , e.g.

```scala

  lazy val swaggerGenerator = SwaggerSpecGenerator("backend")(getClass.getClassLoader)

  implicit val apiDocGenerator = (prefix: Prefix, routes: Seq[Route]) => {
    val doc: JsObject = swaggerGenerator.generateFromRoutes(ListMap(("backend",(prefix.value, routes))))
    Some(doc)
  }
  ``` 
  And then at the play api side you can get the congregated API documentation from asobu `Gateway`
  ```Scala 
  class ApiDocuments @Inject() (gateway: Gateway) extends Controller {
  
  implicit val to: Timeout = 60.seconds

  def specs = Action.async { _ =>
    (gateway.apiDocsRegistry ? Retrieve).mapTo[JsObject].map(Ok(_))
  }
}
```
Example can be found in the [example](/example) project. 


## Kanaloa integration

Asobu aslo support integration with kanaloa sitting between play app and the microservice akka app. 
You need to add the asobu-distributed-kanaloa dependency
```  
  libraryDependencies ++= "com.iheart" %% "asobu-distributed-kanaloa" % version
```
and then in play application add the following class
```scala
package util
class KanaloaBridge @Inject() (implicit config: Configuration, system: ActorSystem) extends AbstractKanaloaBridge {

  override protected def resultChecker: ResultChecker = {
    case e: ErrorResult[_] ⇒ Left(e.toString)
    case m                ⇒ Right(m)
  }
}

```
where `ErrorResult` is your error class. 

Then in the config file, add, 
```
asobu.bridgePropsClass = "util.KanaloaBridge"

```
