package api

trait ErrorResult[T] {
  def inner : T
}
