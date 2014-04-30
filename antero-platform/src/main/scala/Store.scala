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


/**
 * Created by tungtt on 4/5/14.
 */
class Store extends Actor with ActorLogging {
  private var configStore: ConfigStore = _
  private var processor: ActorRef = _
  private var factories: Map[String, ActorRef] = _
  private var countdown: Countdown = _
  private var gatekeeper: ActorRef  = _

  def receive: Actor.Receive = {

    case Config(configStore) =>
      this.configStore = configStore
      processor = configStore.components.getOrElse("processor", sender)

      val channelFactory = context.actorOf(ChannelFactory.props("config/channel.data", configStore))
      val deviceFactory = context.actorOf(DeviceFactory.props("config/device.data"))
      val credentialFactory = context.actorOf(CredentialFactory.props("config/credential.data"))
      val userFactory = context.actorOf(UserFactory.props("config/user.data"))
      val triggerFactory = context.actorOf(TriggerFactory.props("config/trigger.data", configStore))

      factories = Map("channel" -> channelFactory,
                      "trigger" -> triggerFactory,
                      "device" -> deviceFactory,
                      "credential" -> credentialFactory,
                      "user" -> userFactory)

      countdown = Countdown(factories.size)
      factories.values.foreach { factory => factory ! Load(factories) }
      gatekeeper = sender

    case LoadDone(receipt) =>
      countdown.inc
      if (countdown.reset) {
        gatekeeper ! Acknowledge("store")
      }

    case Ready(value) =>
      factories.get("trigger").foreach {ref => ref ! GetAll}

    case HasTrigger(trigger) =>
      import context.dispatcher
      log.info(s"Has trigger $trigger")
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
case class HasCredential(credential: Credential) extends FactoryEvent


class ObjectFactory[A <: Builder](objectFile: String)(implicit m: Manifest[A]) extends Actor with ActorLogging {
  var objects: Map[String, A] = _

  def receive: Actor.Receive = {
    case Load(f) =>
      implicit val jsonFormats: Formats = DefaultFormats
      objects = loadFile(objectFile) {line =>
        val obj = parse(line).extract[A]
        (obj.id, obj)
      }
      sender ! LoadDone(this.getClass.getName)
  }
}

/**
 * Factory for loading devices
 *
 * @param objectFile: device file
 */
class DeviceFactory(objectFile: String) extends ObjectFactory[DeviceBuilder](objectFile) {
  implicit val ec = context.dispatcher
  private var factories: Map[String, ActorRef] = _

  override def receive = {
    case Get(id) =>
      Future {
        objects.get(id).map(d => new Device(d.id, d.name, d.userId, d.proprietaryId))
      } pipeTo sender

    case Load(f) =>
      super.receive.apply(Load(f))
      factories = f
      objects.values
        .map(d => new Device(d.id, d.name, d.userId, d.proprietaryId))
        .foreach(d => factories.get("user").map(ref => ref ! HasDevice(d)))

    case e: FactoryEvent => super.receive.apply(e)
  }
}

object DeviceFactory {
  def props(objectFile: String) = Props(new DeviceFactory(objectFile))
}

/**
 * Factory for loading user credentials
 *
 * @param objectFile: user file
 */
class CredentialFactory(objectFile: String) extends ObjectFactory[TwitterCredentialBuilder](objectFile) {
  implicit val ec = context.dispatcher
  private var factories: Map[String, ActorRef] = _

  override def receive = {
    case Get(id) =>
      Future {
        objects.get(id).map(d => new TwitterCredential(d.id, d.name, d.userId, d.accessKey, d.accessSecret))
      } pipeTo sender

    case Load(f) =>
      super.receive.apply(Load(f))
      factories = f
      objects.values
        .map(d => new TwitterCredential(d.id, d.name, d.userId, d.accessKey, d.accessSecret))
        .foreach(d => factories.get("user").map(ref => ref ! HasCredential(d)))

    case e: FactoryEvent => super.receive.apply(e)
  }
}

object CredentialFactory {
  def props(objectFile: String) = Props(new CredentialFactory(objectFile))
}

/**
 * Factory for loading users
 *
 * @param objectFile: user file
 */
class UserFactory(objectFile: String) extends ObjectFactory[UserBuilder](objectFile) {
  var users: Map[String, User] = _
  implicit val ec = context.dispatcher

  override def receive = {

    case Get(id) =>
      val customer = sender
      Future {
        users.get(id)
      } pipeTo customer

    case HasDevice(device) =>
      users.get(device.userId) foreach {_.addDevice(device)}

    case HasCredential(credential) =>
      log.info("Adding credential")
      users.get(credential.userId) foreach(_.addCredential(credential))

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
  implicit val ec = context.dispatcher

  override def receive = {
    case Get(id) =>
      val customer = sender
      Future {
        channels.get(extractChannelId(id)).map(c => c.event(id))
      } pipeTo customer

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
class TriggerFactory(objectFile: String, val configStore: ConfigStore) extends ObjectFactory[TriggerBuilder](objectFile) {
  implicit val ec = context.dispatcher
  private var factories: Map[String, ActorRef] = _

  override def receive = {
    /*
    case Get(id) =>
      objects.get(id).foreach {b =>
        createTrigger(b) foreach {t => t pipeTo sender }
      }
*/
    case GetAll =>
      for {
        b <- objects.values
        trigger <- createTrigger(b)
        p <- configStore.components.get("processor")
      } yield {
        trigger.foreach(t => t.foreach (tt => p ! RegisterTrigger(tt)))
      }

    case Load(f) =>
      super.receive.apply(Load(f))
      factories = f

    case e: FactoryEvent =>
      super.receive.apply(e)
  }

  private def createTrigger(t: TriggerBuilder): Seq[Future[Option[Trigger]]] = {
    implicit val timeout = Timeout(5, TimeUnit.SECONDS)

    def getEvent = {
      factories.get("channel").map(c => ask(c, Get(t.eventId)).mapTo[Option[Event[AnyRef]]]).toSeq
    }

    def getUser = {
      factories.get("user").map(u => ask(u, Get(t.userName)).mapTo[Option[User]]).toSeq
    }

    for {
      event <- getEvent
      user <- getUser
    } yield build(event, user, t)
  }

  private def build(event: Future[Option[Event[AnyRef]]], user: Future[Option[User]], trigger: TriggerBuilder): Future[Option[Trigger]] = {

    for {
      e <- event
      u <- user
    } yield {
      log.info(s"Assembling trigger  e=$e u=$u id=$trigger.id")
      for {
        ee <- e
        uu <- u
      } yield new Trigger(trigger.id, ee, trigger.variables, uu)
    }
  }
}

object TriggerFactory {
  def props(objectFile: String, configStore: ConfigStore) = Props(new TriggerFactory(objectFile, configStore))
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
                         userId: String,
                         proprietaryId: String) extends Builder

case class TwitterCredentialBuilder(id: String,
                                    name: String,
                                    userId: String,
                                    accessKey: String,
                                    accessSecret: String) extends Builder
/**
 * DataType passed around as message types
 */
sealed trait DataType

case class UserDetails(userName: String) extends DataType
case class TriggerDetails(triggerId: String) extends DataType