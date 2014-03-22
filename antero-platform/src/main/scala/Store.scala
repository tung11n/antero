package antero.store

import akka.actor.{ActorLogging, ActorRef, Actor}
import antero.channel.Channel
import antero.channel.weather.WeatherChannel
import antero.system._
import antero.system.Acknowledge
import antero.system.Config
import antero.system.Ready
import antero.system.{User,Trigger}

/**
 * Created by tungtt on 2/11/14.
 */
class Store extends Actor with ActorLogging {
  private var configStore: ConfigStore = _
  private var processor: ActorRef = _
  private var channels: Map[String,Channel] = _
  private var userManager: UserManager = _

  def receive: Actor.Receive = {

    case Config(configStore) =>
      this.configStore = configStore
      processor = configStore.components.getOrElse("processor", sender)
      channels = Map("weather" -> new WeatherChannel(configStore))
      userManager = UserManager(configStore.configMap)
      sender ! Acknowledge("store")

    case Ready(value) =>
      channels.get("weather") foreach { channel =>
        val predicate = channel.predicate("weather.coldAlert")
        val messageTemplate = channel.messageTemplate("weather.coldWeather")
        val user = userManager.getUser("qwerty")
        val trigger = new Trigger(predicate, 60000, Map("zipCode"->"07642","temp"->"40"), user, messageTemplate)
        processor ! RegisterTrigger(trigger)
      }

    case Retrieve(dataType) =>
      dataType match {
        case UserDetails(userName) =>
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
