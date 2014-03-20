package antero.notification

import akka.actor.{ActorRef, ActorLogging, Actor}
import akka.pattern.pipe
import antero.system._
import antero.system.Acknowledge
import antero.system.Config
import antero.system.Ready
import com.google.android.gcm.server.Sender
import scala.concurrent.Future
import java.io.IOException
import scala.util.Try

/**
 * Created by tungtt on 2/17/14.
 */
class Notifier extends Actor with ActorLogging {
  val MessageRecipient = "antero.message.RECIPIENT"
  val MessagePayload = "antero.message.PAYLOAD"

  var gcmSender: Sender = _
  var registrationId: String = _
  var numberOfWorkers: Int = 5
  var retry: Int = 2

  def receive: Actor.Receive = {

    case Config(configStore) =>
      val configMap = configStore.configMap

      val apiKey = configMap.getOrElse("gcm.apiKey", "")
      gcmSender = new Sender(apiKey)

      numberOfWorkers = configMap.get("notifier.workers").map(n => n.toInt).filter(n => n > 0) getOrElse numberOfWorkers
      retry = configMap.get("notifier.retry").map(n => n.toInt).filter(n => n > 0) getOrElse retry

      sender ! Acknowledge("notifier")

    case Ready(value) =>

    case Notify(user,message) =>
      import context.dispatcher
      user.getDevices.foreach {device =>
        Future(Try(send(device.registrationId, user.userName, message))) pipeTo sender
      }
  }

  @throws(classOf[IOException])
  def send(registrationId: String, recipient: String, msg: String) = {
    val message: com.google.android.gcm.server.Message =
      new com.google.android.gcm.server.Message.Builder().
        addData(MessageRecipient, recipient).
        addData(MessagePayload, msg).
        build()

    log.info(s"Sending notification to ${registrationId}")
    val result = gcmSender.send(message, registrationId, retry)
    if (result.getMessageId == null) {
      throw new RuntimeException(result.getErrorCodeName)
    }
    result.getMessageId
  }
}