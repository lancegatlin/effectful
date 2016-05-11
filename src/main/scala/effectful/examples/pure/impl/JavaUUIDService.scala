package effectful.examples.pure.impl

import java.util.{UUID => JavaUUID}

import effectful._
import effectful.examples.pure.{UUID, UUIDService}

import scala.util.Try

object JavaUUIDService {
  case class Wrapper(value: JavaUUID) extends UUID {
    override def compareTo(o: UUID): Int =
      o match {
        case wrapper:Wrapper =>
          value.compareTo(wrapper.value)
        case _ => throw new UnsupportedOperationException
      }
  }
}

class JavaUUIDService extends UUIDService[Id] {
  import JavaUUIDService._
  override def gen(): Id[UUID] =
    Wrapper(JavaUUID.randomUUID())
  override def parse(s: String): Id[Option[UUID]] =
    Try(JavaUUID.fromString(s)).toOption.map(Wrapper)
}