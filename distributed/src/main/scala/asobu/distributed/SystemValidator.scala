package asobu.distributed

import akka.actor.ActorSystem
import scala.collection.JavaConverters._

object SystemValidator {
  def validate(system: ActorSystem): Either[String, Unit] = {
    val cfg = system.settings.config
    val rolePath = "akka.cluster.distributed-data.role"
    if (!cfg.hasPath(rolePath))
      Left("akka.distributed-data must be enabled")
    else {
      val ddRole = cfg.getString(rolePath)
      val roles = cfg.getStringList("akka.cluster.roles").asScala
      if (!ddRole.isEmpty && !roles.contains(ddRole))
        Left(s"cluster roles (${roles.mkString}) must contain distributed-data scope role $ddRole")
      else
        Right(())
    }
  }
}
