package antero.store

import akka.actor.{Props, ActorLogging, ActorRef, Actor}
import akka.pattern.{ask, pipe}
import antero.system._
import antero.system.Config
import antero.system.Ready
import antero.system.Retrieve
import antero.system.User
import scala.concurrent.Future
import org.json4s.{DefaultFormats, Formats}
import org.json4s.native.JsonMethods._
import scala.Predef._
import akka.util.Timeout
import java.util.concurrent.TimeUnit


/**
 * Created by tungtt on 4/5/14.
 */
class Store extends Actor with ActorLogging {
  private var configStore: ConfigStore = _
  private var processor: ActorRef = _
  private var factories: Map[String, ActorRef] = _
  private var countdown: Countdown = _
  private var storeContext: StoreContext = _

  def receive: Actor.Receive = {

    case Config(configStore) =>
      this.configStore = configStore
      processor = configStore.components.getOrElse("processor", sender)

      val channelFactory = context.actorOf(ChannelFactory.props("channel.data"))
      val deviceFactory = context.actorOf(DeviceFactory.props("device.data", storeContext))
      val userFactory = context.actorOf(UserFactory.props("user.data"))
      val triggerFactory = context.actorOf(TriggerFactory.props("trigger.data", storeContext))

      factories = Map("channel" -> channelFactory,
                      "trigger" -> triggerFactory,
                      "device" -> deviceFactory,
                      "user" -> userFactory)

      storeContext = StoreContext(channelFactory, userFactory, deviceFactory)

      countdown = Countdown(factories.size)
      factories.values.foreach { factory => factory ! Load("store") }

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

case class StoreContext(channelFactory: ActorRef, userFactory: ActorRef, deviceFactory: ActorRef)

/**
 * ObjectFactory is a generic base actor tasked with loading entities from the file system
 *
 */
sealed trait FactoryEvent
case class Load(receipt: String) extends FactoryEvent
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
      objects = Utils.loadFile(objectFile) {line =>
        val obj = parse(line).extract[A]
        (obj.id, obj)
      }
      sender ! LoadDone(receipt)
  }
}

/**
 * Factory for loading devices
 *
 * @param objectFile: device file
 */
class DeviceFactory(objectFile: String, val storeContext: StoreContext) extends ObjectFactory[DeviceBuilder](objectFile) {
  import context.dispatcher

  override def receive = {
    case Get(id) =>
      Future {
        objects.get(id).map[Device](d => new Device(d.id, d.name, d.userId, d.proprietaryId))
      } pipeTo sender

    case Load(receipt) =>
      super.receive.apply(Load(receipt))
      objects.values.map(d => new Device(d.id, d.name, d.userId, d.proprietaryId)).foreach(d => storeContext.userFactory ! HasDevice(d))

    case e: FactoryEvent => super.receive.apply(e)
  }
}

object DeviceFactory {
  def props(objectFile: String, storeContext: StoreContext) = Props(new DeviceFactory(objectFile, storeContext))
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
class ChannelFactory(objectFile: String) extends ObjectFactory[ChannelBuilder](objectFile) {
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

    case e: FactoryEvent =>
      super.receive.apply(e)
  }

  private def extractChannelId(eventId: String) = {
    val d = eventId.indexOf('.')
    if (d != -1) eventId.substring(0, d) else ""
  }
}

object ChannelFactory {
  def props(objectFile: String) = Props(new ChannelFactory(objectFile))
}
/**
 * Factory for loading triggers
 *
 * @param objectFile: trigger file
 */
class TriggerFactory(objectFile: String, val s: StoreContext) extends ObjectFactory[TriggerBuilder](objectFile) {
  lazy val storeContext: StoreContext = s
  import context.dispatcher

  override def receive = {
    case Get(id) =>
      resolve(id) pipeTo sender

    case GetAll =>
      objects.values foreach {t => sender ! HasTrigger(createTrigger(t)) }

    case e: FactoryEvent =>
      super.receive.apply(e)
  }

  def resolve(id: String): Future[Trigger] = {
    objects.get(id) match {
      case Some(t) =>
        createTrigger(t)
      case None =>
        Future { PerpetuallyFalseTrigger }
    }
  }

  private def createTrigger(t: TriggerBuilder): Future[Trigger] = {
    implicit val timeout = Timeout(5, TimeUnit.SECONDS)

    for {
      event <- ask(storeContext.channelFactory, Get(t.eventId)).mapTo[Event]
      user <- ask(storeContext.userFactory, Get(t.userName)).mapTo[User]
    } yield{
      new Trigger(t.id, event, t.variables, user)
    }
  }
}

object TriggerFactory {
  def props(objectFile: String, storeContext: StoreContext) = Props(new TriggerFactory(objectFile, storeContext))
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
                          messageName: String,
                          active: Boolean) extends Builder

case class UserBuilder(id: String,
                       userName: String) extends Builder

case class DeviceBuilder(id: String,
                         name: String,
                         userId: String,
                         proprietaryId: String) extends Builder

/**
 * DataType passed around as message types
 */
sealed trait DataType
case class UserDetails(userName: String) extends DataType
case class TriggerDetails(triggerId: String) extends DataType