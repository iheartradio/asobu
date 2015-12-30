package asobu.dsl.util

import shapeless._
import shapeless.labelled.FieldType

object RecordOps {

  type FieldKV[K, +V] = K with ValueTag[K, V]
  trait ValueTag[K, +V]

  trait FieldKVs[L <: HList] extends DepFn0 with Serializable { type Out <: HList }

  object FieldKVs {

    type Aux[L <: HList, Out0 <: HList] = FieldKVs[L] { type Out = Out0 }

    implicit def hnilFieldKV[L <: HNil]: Aux[L, HNil] =
      new FieldKVs[L] {
        type Out = HNil
        def apply(): Out = HNil
      }

    implicit def hlistFieldKVs[K, V, T <: HList](implicit
      wk: Witness.Aux[K],
      kt: FieldKVs[T]): Aux[FieldType[K, V] :: T, FieldKV[K, V] :: kt.Out] =
      new FieldKVs[FieldType[K, V] :: T] {
        type Out = FieldKV[K, V] :: kt.Out
        def apply(): Out = wk.value.asInstanceOf[FieldKV[K, V]] :: kt()
      }
  }
}
