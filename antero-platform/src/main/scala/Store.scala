package antero.store

import akka.actor.{Props, ActorLogging, ActorRef, Actor}
import akka.pattern.{ask, pipe}
import antero.system._
import antero.system.Acknowledge
import antero.system.Config
import antero.system.Ready
import antero.system.RegisterTrigger
import antero.system.Retrieve
import antero.utils.Utils._
import antero.utils.Utils.Countdown
import scala.concurrent.Future
import org.json4s.{DefaultFormats, Formats}
import org.json4s.native.JsonMethods._
import scala.Predef._
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import scala.Some


/**
 * Created by tungtt on 4/5/14.
 */
class Store extends Actor with ActorLogging {
  private var configStore: ConfigStore = _
  private var processor: ActorRef = _
  private var factories: Map[String, ActorRef] = _
  private var countdown: Countdown = _

  def receive: Actor.Receive = {

    case Config(configStore) =>
      this.configStore = configStore
      processor = configStore.components.getOrElse("processor", sender)

      val channelFactory = context.actorOf(ChannelFactory.props("config/channel.data", configStore))
      val deviceFactory = context.actorOf(DeviceFactory.props("config/device.data"))
      val userFactory = context.actorOf(UserFactory.props("config/user.data"))
      val triggerFactory = context.actorOf(TriggerFactory.props("config/trigger.data"))

      factories = Map("channel" -> channelFactory,
                      "trigger" -> triggerFactory,
                      "device" -> deviceFactory,
                      "user" -> userFactory)

      countdown = Countdown(factories.size)
      factories.values.foreach { factory => factory ! Load(factories) }

    case LoadDone(receipt) =>
      countdown.inc
      if (countdown.reset) {
        sender ! Acknowledge("store")
      }

    case Ready(value) =>
      factories.get("trigger").foreach {ref => ref ! GetAll}

    case HasTrigger(trigger) =>
      import context.dispatcher
      trigger foreach {t => processor ! RegisterTrigger(t) }

    case Retrieve(dataType) =>
      import context.dispatcher

      dataType match {
        case UserDetails(userName) =>
      }
  }
}

/**
 * ObjectFactory is a generic base actor tasked with loading entities from the file system
 *
 */
sealed trait FactoryEvent
case class Load(factories: Map[String, ActorRef]) extends FactoryEvent
case class LoadDone(receipt: String) extends FactoryEvent
case class Get(id: String) extends FactoryEvent
case object GetAll extends FactoryEvent
case class HasTrigger(trigger: Future[Trigger]) extends FactoryEvent
case class HasDevice(device: Device) extends FactoryEvent


class ObjectFactory[A <: Builder](objectFile: String)(implicit m: Manifest[A]) extends Actor with ActorLogging {
  var objects: Map[String, A] = _

  def receive: Actor.Receive = {
    case Load(receipt) =>
      implicit val jsonFormats: Formats = DefaultFormats
      objects = loadFile(objectFile) {line =>
        val obj = parse(line).extract[A]
        (obj.id, obj)
      }
      sender ! LoadDone("done")
  }
}

/**
 * Factory for loading devices
 *
 * @param objectFile: device file
 */
class DeviceFactory(objectFile: String) extends ObjectFactory[DeviceBuilder](objectFile) {
  import context.dispatcher
  private var factories: Map[String, ActorRef] = _

  override def receive = {
    case Get(id) =>
      Future {
        objects.get(id).map[Device](d => new Device(d.id, d.name, d.userName, d.proprietaryId))
      } pipeTo sender

    case Load(f) =>
      super.receive.apply(Load(f))
      factories = f
      objects.values
        .map(d => new Device(d.id, d.name, d.userName, d.proprietaryId))
        .foreach(d => factories.get("user").map(ref => ref ! HasDevice(d)))

    case e: FactoryEvent => super.receive.apply(e)
  }
}

object DeviceFactory {
  def props(objectFile: String) = Props(new DeviceFactory(objectFile))
}

/**
 * Factory for loading users
 *
 * @param objectFile: user file
 */
class UserFactory(objectFile: String) extends ObjectFactory[UserBuilder](objectFile) {
  var users: Map[String, User] = _
  import context.dispatcher

  override def receive = {

    case Get(id) =>
      Future { users.get(id) } pipeTo sender

    case HasDevice(device) =>
      users.get(device.userId) foreach {u => u.addDevice(device)}

    case Load(receipt) =>
      super.receive.apply(Load(receipt))
      users = objects.values.map(u => (u.id, new User(u.id, u.userName))).toMap

    case e: FactoryEvent => super.receive.apply(e)
  }
}

object UserFactory {
  def props(objectFile: String) = Props(new UserFactory(objectFile))
}

/**
 * Factory for loading users
 *
 * @param objectFile: channel file
 */
class ChannelFactory(objectFile: String, val configStore: ConfigStore) extends ObjectFactory[ChannelBuilder](objectFile) {
  var channels: Map[String, Channel] = _
  import context.dispatcher

  override def receive = {
    case Get(id) =>
      Future {
        channels.get(extractChannelId(id)).map(c => c.event(id))
      } pipeTo sender

    case Load(receipt) =>
      super.receive.apply(Load(receipt))
      channels = objects.values.map(c => (c.id, Class.forName(c.channelClass).newInstance.asInstanceOf[Channel])).toMap
      channels.values.foreach(c => c.setConfig(configStore))

    case e: FactoryEvent =>
      super.receive.apply(e)
  }

  private def extractChannelId(eventId: String) = {
    val d = eventId.indexOf('.')
    if (d != -1) eventId.substring(0, d) else ""
  }
}

object ChannelFactory {
  def props(objectFile: String, configStore: ConfigStore) = Props(new ChannelFactory(objectFile, configStore))
}
/**
 * Factory for loading triggers
 *
 * @param objectFile: trigger file
 */
class TriggerFactory(objectFile: String) extends ObjectFactory[TriggerBuilder](objectFile) {
  import context.dispatcher
  private var factories: Map[String, ActorRef] = _

  override def receive = {
    case Get(id) =>
      objects.get(id).foreach {b =>
        createTrigger(b) foreach {t => t pipeTo sender }
      }

    case GetAll =>
      objects.values foreach {b =>
        createTrigger(b) foreach {t => sender ! HasTrigger(t) }
      }

    case Load(f) =>
      super.receive.apply(Load(f))
      factories = f

    case e: FactoryEvent =>
      super.receive.apply(e)
  }
/*
  def resolve(id: String): Future[Trigger] = {
    objects.get(id) match {
      case Some(t) =>
        createTrigger(t)
      case None =>
        Future { PerpetuallyFalseTrigger }
    }
  }
*/
  private def createTrigger(t: TriggerBuilder): Seq[Future[Trigger]] = {
    implicit val timeout = Timeout(5, TimeUnit.SECONDS)

    for {
      event <- factories.get("channel").map(c => ask(c, Get(t.eventId)).mapTo[Event[AnyRef]]).toSeq
      user <- factories.get("user").map(u => ask(u, Get(t.userName)).mapTo[User]).toSeq
    } yield build(event, user, t)
  }

  private def build(event: Future[Event[AnyRef]], user: Future[User], trigger: TriggerBuilder): Future[Trigger] = {
    for {
      e <- event
      u <- user
    } yield new Trigger(trigger.id, e, trigger.variables, u)
  }
}

object TriggerFactory {
  def props(objectFile: String) = Props(new TriggerFactory(objectFile))
}

/**
 * Builder classes for building entity objects from files
 */

sealed trait Builder {
  def id: String
}

case class ChannelBuilder(id: String,
                          channelClass: String) extends Builder

case class TriggerBuilder(id: String,
                          eventId: String,
                          userName: String,
                          variables: Map[String, String],
                          active: Boolean) extends Builder

case class UserBuilder(id: String,
                       userName: String) extends Builder

case class DeviceBuilder(id: String,
                         name: String,
                         userName: String,
                         proprietaryId: String) extends Builder

/**
 * DataType passed around as message types
 */
sealed trait DataType
case class UserDetails(userName: String) extends DataType
case class TriggerDetails(triggerId: String) extends DataType