package asobu

package object distributed {
  type Headers = Seq[(String, String)] //Use Seq instead of Map to better ensure serialization
}
