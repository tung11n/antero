package antero.notification

import akka.actor.{ActorRef, ActorLogging, Actor}
import akka.pattern.pipe
import antero.system._
import antero.system.Acknowledge
import antero.system.Config
import antero.system.Ready
import com.google.android.gcm.server.{Message, Sender}
import scala.concurrent.Future
import java.io.IOException


/**
 * Created by tungtt on 2/17/14.
 */
class Notifier extends Actor with ActorLogging {

  var gcmSender: Sender = _
  var registrationId: String = _
  var numberOfWorkers: Int = 5
  var retry: Int = 2

  def receive: Actor.Receive = {

    case Config(configStore) =>
      gcmSender = new Sender(configStore.getStringSetting("gcm.apiKey"))
      numberOfWorkers = configStore.getIntSetting("notifier.workers", numberOfWorkers)
      retry = configStore.getIntSetting("notifier.retry", retry)

      sender ! Acknowledge("notifier")

    case Ready(value) =>

    case Notify(user, message) =>
      import context.dispatcher
      log.info(s"Notifying user $user")
      user.getDevices.foreach {device =>
        Future {
          try {
            message match {
              case Some(m) =>
                send(device.proprietaryId, m)
              case None =>
            }
            /*
            message foreach {m =>
              log.info(s"Sending message $m to $device")
              send(device.proprietaryId, m)
            }*/
          } catch {
            case e:Exception => log.error(e, "")
          }
        } pipeTo sender
      }
  }

  @throws(classOf[IOException])
  def send(registrationId: String, msg: Map[String, String]) = {
    val messageBuilder = new Message.Builder

    msg.foreach {case (k, v) => messageBuilder.addData(k, v)}

    msg.get("antero.message.PAYLOAD") foreach {m => log.info(s"Sending message $m")}

    val result = gcmSender.send(messageBuilder.build, registrationId, retry)
    if (result.getMessageId == null) {
      throw new RuntimeException(result.getErrorCodeName)
    }

    result.getMessageId
  }
}