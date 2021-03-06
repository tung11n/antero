package antero.system

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import akka.event.LoggingAdapter
import akka.actor.ActorContext

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
case class Result(payload: Any) {

  def extract[A: ClassTag]: A = {
    payload match {
      case _:A => payload.asInstanceOf[A]
      case _ => throw new RuntimeException("Invalid type " + payload.getClass)
    }
  }

  override def toString = payload.toString
}

/**
 * Sandbox specific environment for evaluation of predicates
 */
trait EvaluationContext {
  def actorContext: ActorContext
  def log: LoggingAdapter
  def getVar[A: TypeTag](varName: String): Option[A]
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
  def output(template: A, args: Map[String, String], result: Result): String
}

/**
 * Channel makes available a collection of events to which users can subscribe
 */
abstract class Channel extends Identifiable {
  def setConfig(configStore: ConfigStore): Unit
  def name: String
  def events: Seq[Event[AnyRef]]
  def event(id: String): Event[AnyRef]
  def render(event: Event[AnyRef], variables: Map[String, String], result: Result): Option[String]
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
class Event[+A](val id: String,
            val name: String,
            val interval: Int,
            val channel: Channel,
            val predicate: Predicate,
            val message: A) extends Identifiable

case object NonEvent extends Event("$0", "Non-event", 0xFFFFF, null, null, null)
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
             val proprietaryId: String) extends Identifiable {
  override def toString = name
}

/**
 * Represents an user, devices and credentials registered under this user
 *
 * @param id
 * @param userName
 */
class User(val id: String, val userName: String) extends Identifiable {
  private var devices = List[Device]()
  private var credentials = List[Credential]()

  def getDevices = devices
  def getCredentials = credentials

  def addDevice(device: Device) = devices = device::devices

  def addCredential(credential: Credential) = credentials = credential::credentials

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
              val event: Event[AnyRef],
              val variables: Map[String, String],
              val user: User) extends Identifiable

case object PerpetuallyFalseTrigger extends Trigger("", null, Map(), DefaultUser)

trait Credential extends Identifiable {
  val name: String
  val userId: String
}

class TwitterCredential(val id: String, val name: String, val userId: String, val tokenKey: String, val tokenSecret: String) extends Credential