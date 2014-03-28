package antero.system

import antero.processor.EvalContext
import scala.reflect.ClassTag

/**
 * Created by tungtt on 2/8/14.
 */
trait Predicate {
  def evaluate(context: EvalContext): Option[Result]
}

trait MessageTemplate {
  def output(args: Map[String,String], result: Result): String
}
/*
class Event {
  def predicate: Predicate
  def message: MessageTemplate
}*/

class Result(val payload: Any) {

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

  override def toString = userName
}

class Device(val name: String, val registrationId: String)


class Trigger(val predicate: Predicate,
              val interval: Int,
              val variables: Map[String, String],
              val user: User,
              val template: MessageTemplate)

object Trigger {
  def defaultTrigger: Trigger = {
    val defaultPredicate: Predicate = new Predicate {
      def evaluate(context: EvalContext): Option[Result] = None
    }
    val defaultMessageTemplate = new MessageTemplate {
      def output(args: Map[String, String], result: Result): String = ""
    }
    new Trigger(defaultPredicate, 0, Map(), new User(""), defaultMessageTemplate)
  }
}

class TriggerBuilder(val id: String,
              val channelName: String,
              val predicateName: String,
              val interval: Int,
              val variables: Map[String, String],
              val userName: String,
              val templateName: String)