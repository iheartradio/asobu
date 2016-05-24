package asobu.distributed.util

import org.specs2.matcher._

import scala.util.control.NonFatal
import scala.language.implicitConversions
import MatchersCreation._

trait SerializableTest {
  def isSerializable[T](a: T): Boolean = {
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

  def beSerializable[T]: Matcher[T] = { t: T ⇒
    (isSerializable(t), s" ${t} is not serializable")
  }
}
