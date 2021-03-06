package net.liftmodules.ng

import Angular._

import net.liftweb._
import json._
import actor.LAFuture
import common._

object LAFutureSerializer {
  def laFuture2JValue[T](formats: Formats, future: LAFuture[Box[T]]) = {
    implicit val f = formats + new LAFutureSerializer

    val id = rand
    val flagField = JField("net.liftmodules.ng.Angular.future", JBool(true))
    val fields = flagField +:
      (if (!future.isSatisfied) {
        plumbFuture(future, id)
        List(JField("id", JString(id)))
      } else {
        future.get match {
          case Full(data) => List(JField("data", Extraction.decompose(data)))
          case Failure(msg, _, _) => List(JField("msg", JString(msg)))
          case Empty => List()
        }
      })

    JObject(fields)
  }

  def laFutureSerializer(formats: Formats): PartialFunction[Any, JValue] = {
    case future: LAFuture[Box[_]] => laFuture2JValue(formats, future)
  }

}
class LAFutureSerializer[T <: NgModel : Manifest] extends Serializer[LAFuture[Box[T]]] with ScalaFutureSerializer {
  import LAFutureSerializer._
  import AngularExecutionContext._

  private def deserializer:PartialFunction[JValue, LAFuture[Box[T]]] = {
    case JObject(List(JField("net.liftmodules.ng.Angular.futureId", JString(id)))) => {
      val future = new LAFuture[Box[T]]()
      future.abort()
      future
    }
  }

  // The stuff below was copy/pasted from CustomSerializer.  Because we need to recursively call ourselves,
  // it's not possible to use CustomSerializer.
  val Class = implicitly[Manifest[LAFuture[Box[T]]]].erasure

  def deserialize(implicit format: Formats) = {
    case (TypeInfo(Class, _), json) =>
      if (deserializer.isDefinedAt(json)) deserializer(json)
      else throw new MappingException("Can't convert " + json + " to " + Class)
  }

  def serialize(implicit format: Formats) = laFutureSerializer(format) orElse scalaFutureSerializer(format)
}