package asobu.distributed.util

import akka.actor.ActorSystem

import scala.util.control.NonFatal

trait SerializableTest {
  def isSerializable[T](a: T)(implicit system: ActorSystem = ActorSystem()): Boolean = {
    import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
    val baos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(baos)
    var ois: ObjectInputStream = null
    try {
      oos.writeObject(a)
      oos.close()
      val bais = new ByteArrayInputStream(baos.toByteArray())
      ois = new ObjectInputStream(bais)
      val a2 = ois.readObject()
      ois.close()
      true
    } catch {
      case NonFatal(t) ⇒
        throw new Exception(t)
    } finally {
      oos.close()
      if (ois != null) ois.close()
    }
  }
}
