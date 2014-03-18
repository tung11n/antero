package antero.store

import akka.actor.{ActorLogging, ActorRef, Actor}
import antero.channel.Channel
import antero.channel.weather.WeatherChannel
import antero.system.{Ready, Acknowledge, ConfigStore, Config}
import antero.trigger.Trigger

/**
 * Created by tungtt on 2/11/14.
 */
class Store extends Actor with ActorLogging {
  private var configStore: ConfigStore = _
  private var processorRef: ActorRef = _
  private var channels: Map[String,Channel] = _

  def receive: Actor.Receive = {

    case Config(configStore) =>
      this.configStore = configStore
      processorRef = configStore.components.getOrElse("processor", sender)
      initializeChannels
      sender ! Acknowledge("store")

    case Ready(value) =>
      channels.get("weather") foreach { channel =>
        val predicate = channel.predicate("weather.coldAlert")
        val messageTemplate = channel.messageTemplate("weather.coldWeather")
        processorRef ! Trigger(predicate, 60000, Map("zipCode"->"07642","temp"->"40"), null, messageTemplate)
      }
  }

  def initializeChannels: Unit = {
    channels = Map("weather" -> new WeatherChannel(configStore))
  }
}