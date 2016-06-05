package asobu.distributed

import akka.actor._
import akka.cluster.{Member, MemberStatus, Cluster}
import akka.cluster.ddata.{LWWMap, LWWMapKey, DistributedData}
import akka.cluster.ddata.Replicator._
import asobu.distributed.EndpointsRegistry.DocDataType
import play.api.libs.json.{JsNumber, Json, JsObject}
import concurrent.duration._

trait EndpointsRegistry {
  val EndpointsDataKey = EndpointsRegistry.EndpointsDataKey
  val EndpointsDocsKey = EndpointsRegistry.EndpointsDocsKey

  def writeConsistency: WriteConsistency
  def readConsistency: ReadConsistency
  def emptyData: LWWMap[EndpointDefinition]
  def emptyDocs: LWWMap[DocDataType]

  implicit def node: Cluster
  def replicator: ActorRef
}

object EndpointsRegistry {

  //Doc json is stored as String (JsObject isn't that serializable after manipulation)
  type DocDataType = String

  val EndpointsDataKey = LWWMapKey[EndpointDefinition]("endpoints-registry-endpoints")
  val EndpointsDocsKey = LWWMapKey[DocDataType]("endpoints-registry-apidocs")
}

case class DefaultEndpointsRegistry(system: ActorSystem) extends EndpointsRegistry {
  val timeout = 30.seconds

  val writeConsistency = WriteAll(timeout)
  val readConsistency = ReadMajority(timeout)
  val emptyData = LWWMap.empty[EndpointDefinition]
  val emptyDocs = LWWMap.empty[DocDataType]

  implicit val node = Cluster(system)
  val replicator: ActorRef = DistributedData(system).replicator
}

class EndpointsRegistryUpdater(registry: EndpointsRegistry) extends Actor with ActorLogging {
  import EndpointsRegistryUpdater._
  import registry._

  def receive: Receive = {
    case Add(endpointDef) ⇒
      update(Added(sender)) { m ⇒
        m + (endpointDef.id → endpointDef)
      }

    case AddDoc(role, doc) ⇒
      log.info(s"Registering Api Documentation for $role")
      updateDoc(Added(sender))(_ + (role → Json.stringify(doc)))

    case Remove(role) ⇒
      removeEndpoint(sender) { endpointDef ⇒
        endpointDef.clusterRole == role
      }
      updateDoc(Removed(sender))(_ - role)

    case Sanitize ⇒
      def inCluster(member: Member): Boolean =
        List(MemberStatus.Up, MemberStatus.Joining, MemberStatus.WeaklyUp).contains(member.status)

      val currentRoles = Cluster(context.system).state.members.filter(inCluster).flatMap(_.roles).toSet

      log.info("Sanitizing current endpoint defs based current roles " + currentRoles.mkString(", "))
      removeEndpoint(sender) { ef ⇒
        !currentRoles.contains(ef.clusterRole)
      }

      updateDoc(Removed(sender)) { m ⇒
        m.entries.keys.foldLeft(m) { (lwwMap, role) ⇒
          if (!currentRoles.contains(role))
            lwwMap - role
          else
            lwwMap
        }
      }

    case UpdateSuccess(_, Some(result: Result)) ⇒
      log.info(s"EndpointRegistry updated by $result")
      result.confirm()
  }

  def update(res: Result)(f: LWWMap[EndpointDefinition] ⇒ LWWMap[EndpointDefinition]): Unit = {
    replicator ! Update(EndpointsDataKey, emptyData, writeConsistency, Some(res))(f)
  }

  def updateDoc(res: Result)(f: LWWMap[DocDataType] ⇒ LWWMap[DocDataType]): Unit = {
    replicator ! Update(EndpointsDocsKey, emptyDocs, writeConsistency, Some(res))(f)
  }

  def removeEndpoint(sender: ActorRef)(predictor: EndpointDefinition ⇒ Boolean): Unit =
    update(Removed(sender)) { m ⇒
      m.entries.values.foldLeft(m) { (lwwMap, endpointDef) ⇒
        if (predictor(endpointDef))
          lwwMap - endpointDef.id
        else
          lwwMap
      }
    }

}

object EndpointsRegistryUpdater {

  def props(registry: EndpointsRegistry) = Props(new EndpointsRegistryUpdater(registry))

  sealed trait UpdateRequest

  case class Add(endpointDef: EndpointDefinition) extends UpdateRequest

  case class AddDoc(role: String, doc: JsObject)

  case class Remove(role: String) extends UpdateRequest

  /**
   * Remove all endpoints whose role isn't in this list.
   */
  case object Sanitize extends UpdateRequest

  sealed trait Result {
    def replyTo: ActorRef
    def confirm(): Unit = {
      replyTo ! this
    }
  }

  case class Added(replyTo: ActorRef) extends Result
  case class Removed(replyTo: ActorRef) extends Result
  case class Checked(replyTo: ActorRef) extends Result

}

