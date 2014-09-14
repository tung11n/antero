package antero.utils

import antero.system._
import scala.reflect.runtime.universe._

/**
 * Created by tungtt on 2/19/14.
 */
object Utils {

  def getStringSetting(varName: String, fallbackValue: String = "")(implicit configMap: Map[String,String]): String = {
    configMap.getOrElse(varName, fallbackValue)
  }

  def getIntSetting(varName: String,fallbackValue: Int = 0)(implicit configMap: Map[String,String]): Int = {
    configMap.get(varName).map(n => n.toInt).filter(n => n > 0) getOrElse fallbackValue
  }

  def loadFile[A](fileName: String)(f: String=>(String,A)): Map[String,A] = {
    scala.io.Source.fromFile(fileName).
      getLines().
      filter(line => !line.startsWith("#")).
      map(line=>f(line)).
      toMap
  }

  /**
   * Implicits
   */
  implicit object SimpleMessageTemplate extends MessageTemplate[String] {
    val Sub = """\$(\$|\w+)""".r

    def output(template: String, args: Map[String, String], result: Result) = {
      Sub.replaceAllIn(template, m => if (m.group(1) == "$") result.toString else args.get(m.group(1)).getOrElse(""))
    }
  }

  def renderMessage[A: MessageTemplate](message: A, variables: Map[String, String], result: Result) = {
    implicitly[MessageTemplate[A]].output(message, variables, result)
  }

  case class Countdown(var count: Int) {
    def inc = {
      if (count > 0) count - 1 else count
    }

    def reset: Boolean = count == 0
  }
}

object Conversion {

  case class ParseOp[T](op: String => T)

  implicit val popDouble = ParseOp[Double](_.toDouble)
  implicit val popLong = ParseOp[Long](_.toLong)
  implicit val popInt = ParseOp[Int](_.toInt)

  def parseString[T: ParseOp](s: String) = Some(implicitly[ParseOp[T]].op(s))

  def convertTo[A: TypeTag](vars: Map[String, String])(varName: String): Option[A] = {
    val value = vars.get(varName)
    val returned = typeOf[A] match {
      case t if t =:= typeOf[Int] => value.map(v=>parseString[Int](v)).flatten
      case t if t =:= typeOf[Long] => value.map(v=>parseString[Long](v)).flatten
      case t if t =:= typeOf[Double] => value.map(v=>parseString[Double](v)).flatten
      case t if t =:= typeOf[String] => value
    }
    returned.asInstanceOf[Option[A]]
  }
}