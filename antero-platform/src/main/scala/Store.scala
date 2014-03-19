package antero.store

import akka.actor.{ActorLogging, ActorRef, Actor}
import antero.channel.Channel
import antero.channel.weather.WeatherChannel
import antero.system._
import antero.system.Acknowledge
import antero.system.Config
import antero.system.Ready
import antero.system.{User,Trigger,Predicate}

/**
 * Created by tungtt on 2/11/14.
 */
class Store extends Actor with ActorLogging {
  private var configStore: ConfigStore = _
  private var processorRef: ActorRef = _
  private var channels: Map[String,Channel] = _
  private var users: Map[String, User] = _

  def receive: Actor.Receive = {

    case Config(configStore) =>
      this.configStore = configStore
      processorRef = configStore.components.getOrElse("processor", sender)
      initialize
      sender ! Acknowledge("store")

    case Ready(value) =>
      channels.get("weather") foreach { channel =>
        val predicate = channel.predicate("weather.coldAlert")
        val messageTemplate = channel.messageTemplate("weather.coldWeather")
        val trigger = new Trigger(predicate, 60000, Map("zipCode"->"07642","temp"->"40"), null, messageTemplate)
        processorRef ! RegisterTrigger(trigger)
      }

    case Retrieve(dataType) =>
      dataType match {
        case UserDetails(userName) =>
      }
      
  }

  def initialize: Unit = {
    channels = Map("weather" -> new WeatherChannel(configStore))
  }
}

sealed trait DataType

case class UserDetails(userName: String) extends DataType
