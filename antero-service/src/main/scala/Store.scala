package antero.store

import akka.actor.{ActorLogging, ActorRef, Props, Actor}
import antero.processor.EvalContext
import antero.system.{Ready, Acknowledge, ConfigStore, Config}
import antero.trigger.{SealedTriggerCommand, Trigger}

/**
 * Created by tungtt on 2/11/14.
 */
class Store extends Actor with ActorLogging {
  private var configStore: ConfigStore = _
  private var processorRef: ActorRef = _

  def receive: Actor.Receive = {
    case Config(configStore) =>
      this.configStore = configStore
      processorRef = configStore.components.getOrElse("processor", sender)
      sender ! Acknowledge("store")

    case Ready(value) =>
      val command = new SealedTriggerCommand {
        def evaluate(context: EvalContext): Boolean =  {
          true
        }
      }
      val trigger = Trigger(command, 10000)
      processorRef ! trigger
  }
}
