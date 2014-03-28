package antero.store

import akka.actor.{Props, ActorLogging, ActorRef, Actor}
import akka.pattern.pipe
import antero.channel.Channel
import antero.channel.weather.WeatherChannel
import antero.system._
import antero.system.Acknowledge
import antero.system.Config
import antero.system.Ready
import antero.system.{User,Trigger}
import scala.concurrent.Future
import org.json4s.native.JsonMethods._
import org.json4s.{DefaultFormats, Formats}

/**
 * Created by tungtt on 2/11/14.
 */
class Store extends Actor with ActorLogging {
  private var configStore: ConfigStore = _
  private var processor: ActorRef = _
  private var channels: Map[String,Channel] = _
  private var userManager: UserManager = _
  private var triggerFactory: ActorRef = _

  def receive: Actor.Receive = {

    case Config(configStore) =>
      this.configStore = configStore
      processor = configStore.components.getOrElse("processor", sender)
      channels = Map("weather" -> new WeatherChannel(configStore))
      userManager = UserManager(configStore.configMap)
      triggerFactory = context.actorOf(Props(classOf[TriggerFactory], configStore, channels, userManager, processor))

      sender ! Acknowledge("store")

    case Ready(value) =>
      triggerFactory ! Ready(value)

    case Retrieve(dataType) =>
      import context.dispatcher

      dataType match {
        case UserDetails(userName) =>
          Future { userManager.getUser(userName) } pipeTo sender
      }
      
  }
}

class TriggerFactory(configStore: ConfigStore,
                     val channels: Map[String,Channel],
                     val userManager: UserManager,
                     val processor: ActorRef) extends Actor with ActorLogging {

  val triggers: Map[String, Trigger] = loadTriggers(configStore.getStringSetting("store.trigger.file"))

  def receive: Actor.Receive = {
    case Ready(value) =>
      triggers foreach { case (id,trigger) =>
        processor ! RegisterTrigger(trigger)
      }

    case Retrieve(dataType) =>
      import context.dispatcher

      dataType match {
        case TriggerDetails(triggerId) =>
          triggers.get(triggerId).orElse(None)
      }
  }

  private def loadTriggers(triggerFile: String): Map[String, Trigger] = {
    val triggerBuilders: Map[String,TriggerBuilder] = Utils.loadFile(triggerFile) {line =>
      implicit val jsonFormats: Formats = DefaultFormats
      val triggerBuilder = parse(line).extract[TriggerBuilder]
      (triggerBuilder.id, triggerBuilder)
    }

    triggerBuilders.map { case (id,builder) =>
      val t = channels.get(builder.channelName) match {
        case Some(channel) =>
          val predicate = channel.predicate(builder.predicateName)
          val messageTemplate = channel.messageTemplate(builder.templateName)
          val user = userManager.getUser(builder.userName)
          log.info("Loading trigger " + builder)

          new Trigger(predicate, builder.interval, builder.variables, user, messageTemplate)
        case None =>
          Trigger.defaultTrigger
      }
      (id,t)
    }
  }
}

class UserManager {
  private var users: Map[String, User] = _
  private var registrationId: String = _
  private val defaultUser: User = new User("")

  def getUser(userName: String): User = users.get(userName).getOrElse(defaultUser)
}

object UserManager {
  val userManager = new UserManager

  def apply(configMap: Map[String, String]) = {
    userManager.registrationId = configMap.getOrElse("gcm.registrationId", "")
    val user = new User("qwerty")
    user.addDevice(new Device("Terminal", userManager.registrationId))
    userManager.users = Map("qwerty" -> user)

    userManager
  }
}

sealed trait DataType

case class UserDetails(userName: String) extends DataType

case class TriggerDetails(triggerId: String) extends DataType