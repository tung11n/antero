package antero.message

import akka.actor.{ActorRef, ActorLogging, Actor}
import antero.system._
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

  def receive: Actor.Receive = {
    case Config(configStore) =>
      sender ! Acknowledge("messageBuilder")

    case Build(result, trigger) =>
      result foreach {r =>
        log.info("Building message")
        buildMessage(trigger.template, r, trigger.variables) pipeTo sender
      }
  }

  def buildMessage(template: MessageTemplate, result: Result, args: Map[String,String]): Future[String] = {
    Future { template.output(args, result) }
  }
}
