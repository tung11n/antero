package antero.trigger

import antero.processor.EvalContext
import scala.reflect.ClassTag

/**
 * Created by tungtt on 2/8/14.
 */
trait Predicate {
  def evaluate(context: EvalContext): Result
}

trait MessageTemplate {
  def output(args: Map[String,String], result: Result): String
}

class Result(val predicateMet: Boolean, val payload: Any) {

  def isSatisfied = predicateMet

  def extract[A: ClassTag]: A = {
    payload match {
      case _:A => payload.asInstanceOf[A]
      case _ => throw new RuntimeException("Invalid type " + payload.getClass)
    }
  }
}

class User {
  private var id: Long = _
  private var userName: String = _
  private var devices = List[Device]()
}

case class Device(name: String, registerId: String)

case class Trigger(predicate: Predicate,
                   interval: Int,
                   variables: Map[String, String] = Map(),
                   user: User = null,
                   template: MessageTemplate)