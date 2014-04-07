package antero.system

import scala.reflect.ClassTag

/**
 * Created by tungtt on 2/8/14.
 */

/**
 * Applied to objects that can be identified with an String id
 */
trait Identifiable {
  def id: String
}

/**
 * Result of a predicate's evaluation
 *
 * @param payload
 */
class Result(val payload: Any) {

  def extract[A: ClassTag]: A = {
    payload match {
      case _:A => payload.asInstanceOf[A]
      case _ => throw new RuntimeException("Invalid type " + payload.getClass)
    }
  }
}

/**
 * Sandbox specific environment for evaluation of predicates
 */
trait EvaluationContext {
  def getVar[A](varName: String): String
}

/**
 * Predicate defines a condition under which a trigger is evaluated
 */
abstract class Predicate {
  def evaluate(context: EvaluationContext): Option[Result]
}

/**
 * Message sent to user when an event is triggered
 */
abstract class MessageTemplate[A] {
  def output(args: Map[String, String], result: Result): A
}

/**
 * Channel makes available a collection of events to which users can subscribe
 */
abstract class Channel extends Identifiable {
  def name: String
  def events: Seq[Event]
  def event(id: String): Event
}

/**
 * A particular event to which users may subscribe. Event is always associated with a channel
 *
 * @param id
 * @param name
 * @param interval
 * @param channel
 * @param predicate
 * @param message
 */
class Event(val id: String,
            val name: String,
            val interval: Int,
            val channel: Channel,
            val predicate: Predicate,
            val message: MessageTemplate[String]) extends Identifiable

case object NonEvent extends Event("$0", "None-event", 0xFFFFF, null, null, null)
/**
 * A terminal device to which messages are sent
 *
 * @param id
 * @param name
 * @param proprietaryId
 */
class Device(val id: String,
             val name: String,
             val userId: String,
             val proprietaryId: String) extends Identifiable

/**
 * Represents an user and devices registered under this user
 *
 * @param id
 * @param userName
 */
class User(val id: String, val userName: String) extends Identifiable {
  private var devices = List[Device]()

  def getDevices = devices

  def addDevice(device: Device) = {
    devices = device::devices
  }

  override def toString = userName
}

case object DefaultUser extends User("$0","$system")

/**
 * A custom event associated with a particular user
 *
 * @param id
 * @param event
 * @param variables
 * @param user
 */
class Trigger(val id: String,
              val event: Event,
              val variables: Map[String, String],
              val user: User) extends Identifiable

case object PerpetuallyFalseTrigger extends Trigger("", null, Map(), DefaultUser)
