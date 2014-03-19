package antero.message

import akka.actor.{ActorRef, ActorLogging, Actor}
import antero.system.{Build, Acknowledge, Fire, Config,MessageTemplate, Trigger, Result}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import akka.pattern.{ask,pipe}
import akka.util.Timeout
import java.util.concurrent.{Executors, TimeUnit}

/**
 * Created by tungtt on 3/11/14.
 */
class MessageBuilder extends Actor with ActorLogging {
  implicit val timeout = Timeout(5, TimeUnit.SECONDS)
  implicit val ec = context.dispatcher

  var notifier: ActorRef = _

  def receive: Actor.Receive = {
    case Config(configStore) =>
      notifier = configStore.components.getOrElse("notifier", sender)
      sender ! Acknowledge("messageBuilder")

    case Build(result, trigger) =>
      val message = buildMessage(trigger.template, result, trigger.variables)
      val r = ask(notifier, Fire(trigger.user, message))
      r onSuccess {
        case Success(v) => log.info("Message delivered to owner's device(s). MESSAGE-ID=" + v)
        case Failure(e) => log.error("ERROR ", e)
      }
      r pipeTo sender
  }

  def buildMessage(template: MessageTemplate, result: Result, args: Map[String,String]): String = {
    template.output(args, result)
//    result map {r => val s = template.output(args, r);log.info(s);s}
  }

}
