package antero.system

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

class User(val userName: String) {
  private var devices = List[Device]()

  def getDevices = devices

  def addDevice(device: Device) = {
    devices = device::devices
  }
}

class Device(val name: String, val registerId: String)

class Trigger(val predicate: Predicate,
              val interval: Int,
              val variables: Map[String, String],
              val user: User,
              val template: MessageTemplate)