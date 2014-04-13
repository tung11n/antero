package antero.message

import akka.actor.{ActorRef, ActorLogging, Actor}
import antero.system.Acknowledge
import antero.system.Build
import antero.system.Config
import scala.concurrent.Future
import akka.pattern.pipe
import akka.util.Timeout
import java.util.concurrent.TimeUnit

/**
 * Created by tungtt on 3/11/14.
 */
class MessageBuilder extends Actor with ActorLogging {
  implicit val timeout = Timeout(5, TimeUnit.SECONDS)
  implicit val ec = context.dispatcher

  val MessageRecipient = "antero.message.RECIPIENT"
  val MessagePayload = "antero.message.PAYLOAD"

  def receive: Actor.Receive = {
    case Config(configStore) =>
      sender ! Acknowledge("messageBuilder")

    case Build(result, trigger) =>
      Future {
        result map { r =>
          trigger.event.channel.render(trigger.event, trigger.variables, r) foreach { rendered =>
          Map(
            MessageRecipient -> trigger.user.userName,
            MessagePayload -> rendered
          ) }
        }
      } pipeTo sender
  }
}