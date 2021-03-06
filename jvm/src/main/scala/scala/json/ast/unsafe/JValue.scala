package scala.json.ast.unsafe

import java.util

import scala.json.ast
import scala.json.ast._

/** Represents a JSON Value which may be invalid. Internally uses mutable
  * collections when its desirable to do so, for performance and other reasons
  * (such as ordering and duplicate keys)
  *
  * @author Matthew de Detrich
  * @see https://www.ietf.org/rfc/rfc4627.txt
  */
sealed abstract class JValue extends Serializable with Product {

  /**
    * Converts a [[unsafe.JValue]] to a [[ast.JValue]]. Note that
    * when converting [[unsafe.JNumber]], this can throw runtime error if the underlying
    * string representation is not a correct number. Also when converting a [[unsafe.JObject]]
    * to a [[ast.JObject]], its possible to lose data if you have duplicate keys.
    *
    * @see https://www.ietf.org/rfc/rfc4627.txt
    * @return
    */
  def toStandard: ast.JValue
}

/** Represents a JSON null value
  *
  * @author Matthew de Detrich
  */
case object JNull extends JValue {
  override def toStandard: ast.JValue = ast.JNull
}

/** Represents a JSON string value
  *
  * @author Matthew de Detrich
  */
case class JString(value: String) extends JValue {
  override def toStandard: ast.JValue = ast.JString(value)
}

object JNumber {
  def apply(value: Int): JNumber = JNumber(value.toInt.toString)

  def apply(value: Short): JNumber = JNumber(value.toString)

  def apply(value: Long): JNumber = JNumber(value.toString)

  def apply(value: BigInt): JNumber = JNumber(value.toString)

  def apply(value: BigDecimal): JNumber = JNumber(value.toString)

  def apply(value: Float): JNumber = JNumber(value.toString)

  def apply(value: Double): JNumber = JNumber(value.toString)

  def apply(value: Integer): JNumber = JNumber(value.toString)

  def apply(value: Array[Char]): JNumber = JNumber(value.mkString)
}

/** Represents a JSON number value.
  *
  * If you are passing in a NaN or Infinity as a [[Double]], [[unsafe.JNumber]]
  * will contain "NaN" or "Infinity" as a String which means it will cause
  * issues for users when they use the value at runtime. It may be
  * preferable to check values yourself when constructing [[unsafe.JValue]]
  * to prevent this. This isn't checked by default for performance reasons.
  *
  * @author Matthew de Detrich
  */
// JNumber is internally represented as a string, to improve performance
case class JNumber(value: String) extends JValue {
  def to[B](implicit jNumberConverter: JNumberConverter[B]): B =
    jNumberConverter(value)

  override def toStandard: ast.JValue = ast.JNumber(value)
}

/** Represents a JSON Boolean value, which can either be a
  * [[JTrue]] or a [[JFalse]]
  *
  * @author Matthew de Detrich
  */
// Implements named extractors so we can avoid boxing
sealed abstract class JBoolean extends JValue {
  def get: Boolean
}

object JBoolean {
  def apply(x: Boolean): JBoolean = if (x) JTrue else JFalse

  def unapply(x: JBoolean): Some[Boolean] = Some(x.get)
}

/** Represents a JSON Boolean true value
  *
  * @author Matthew de Detrich
  */
case object JTrue extends JBoolean {
  override def get = true

  override def toStandard: ast.JValue = ast.JTrue
}

/** Represents a JSON Boolean false value
  *
  * @author Matthew de Detrich
  */
case object JFalse extends JBoolean {
  override def get = false

  override def toStandard: ast.JValue = ast.JFalse
}

case class JField(field: String, value: JValue)

object JObject {
  def apply(value: JField, values: JField*): JObject =
    JObject(Array(value) ++ values)
}

/** Represents a JSON Object value. Duplicate keys
  * are allowed and ordering is respected
  * @author Matthew de Detrich
  */
// JObject is internally represented as a mutable Array, to improve sequential performance
case class JObject(value: Array[JField] = Array.empty) extends JValue {
  override def toStandard: ast.JValue = {
    val length = value.length
    if (length == 0) {
      ast.JObject(Map[String, ast.JValue]())
    } else {
      var index = 0
      val b = Map.newBuilder[String, ast.JValue]
      while (index < length) {
        val v = value(index)
        b += ((v.field, v.value.toStandard))
        index += 1
      }
      ast.JObject(b.result())
    }
  }

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case jObject: JObject =>
        val length = value.length
        if (length != jObject.value.length)
          return false
        var index = 0
        while (index < length) {
          if (value(index) != jObject.value(index))
            return false
          index += 1
        }
        true
      case _ => false
    }
  }

  override def hashCode: Int =
    java.util.Arrays.deepHashCode(value.asInstanceOf[Array[AnyRef]])
}

object JArray {
  def apply(value: JValue, values: JValue*): JArray =
    JArray(Array(value) ++ values.to[Array])
}

/** Represents a JSON Array value
  * @author Matthew de Detrich
  */
// JArray is internally represented as a mutable Array, to improve sequential performance
case class JArray(value: Array[JValue] = Array.empty) extends JValue {
  override def toStandard: ast.JValue = {
    val length = value.length
    if (length == 0) {
      ast.JArray(Vector[ast.JValue]())
    } else {
      var index = 0
      val b = Vector.newBuilder[ast.JValue]
      while (index < length) {
        b += value(index).toStandard
        index += 1
      }
      ast.JArray(b.result())
    }
  }

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case jArray: JArray =>
        val length = value.length
        if (length != jArray.value.length)
          return false
        var index = 0
        while (index < length) {
          if (value(index) != jArray.value(index))
            return false
          index += 1
        }
        true
      case _ => false
    }
  }

  override def hashCode: Int =
    java.util.Arrays.deepHashCode(value.asInstanceOf[Array[AnyRef]])
}
