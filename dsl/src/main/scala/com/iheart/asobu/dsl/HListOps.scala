package asobu.dsl

import shapeless.ops.hlist.Remove
import shapeless.{::, HNil, HList}

object HListOps {
  trait RestOf[L <: HList, SL <: HList] {
    type Out <: HList
  }

  object RestOf {

    type Aux[L <: HList, SL <: HList, Out0 <: HList] = RestOf[L, SL] {
      type Out = Out0
    }

    implicit def hlistRestOfNil[L <: HList]: Aux[L, HNil, L] = new RestOf[L, HNil] { type Out = L }

    implicit def hlistRestOf[L <: HList, E, RemE <: HList, Rem <: HList, SLT <: HList](implicit rt: Remove.Aux[L, E, (E, RemE)], st: Aux[RemE, SLT, Rem]): Aux[L, E :: SLT, Rem] =
      new RestOf[L, E :: SLT] { type Out = Rem }
  }
}
