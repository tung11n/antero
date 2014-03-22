package antero.system

import akka.kernel.Bootable
import akka.actor._

import antero.message.MessageBuilder
import antero.notification.Notifier
import antero.processor.Processor
import antero.store.{DataType, Store}
import scala.concurrent.Future

/**
 * Created by tungtt on 2/11/14.
 */

class Bootstrap extends Bootable{
  val system = ActorSystem("antero")

  def startup = {
    val gatekeeper = system.actorOf(Props(classOf[Gatekeeper], "./config/antero.properties"))
    gatekeeper ! Ready("config")
  }

  def shutdown = {
    system.shutdown()
  }
}

class ConfigStore() {
  var configMap: Map[String,String] = _
  var components: Map[String, ActorRef] = Map()

  def addComponent(key: String, componentRef: ActorRef) = components += (key -> componentRef)

  def getStringSetting(varName: String, fallbackValue: String = ""): String = {
    configMap.getOrElse(varName, fallbackValue)
  }

  def getIntSetting(varName: String,fallbackValue: Int = 0): Int = {
    configMap.get(varName).map(n => n.toInt).filter(n => n > 0) getOrElse fallbackValue
  }

  override def toString = {
    super.toString
  }
}

object ConfigStore {

  def apply(fileName: String) = {
    val configStore = new ConfigStore()
    configStore.configMap = loadConfig(fileName)
    configStore
  }

  private def loadConfig(fileName: String): Map[String,String] = {
    scala.io.Source.fromFile(fileName).
      getLines().
      filter(line => !line.startsWith("#")).
      map({ line =>
        val s = line.split("=")
        if (s.size == 2) (s(0).trim, s(1).trim) else (s(0).trim,"")
      }).
      toMap
  }

}

class Gatekeeper(fileName: String) extends Actor with ActorLogging {
  private var configStore: ConfigStore = _
  private var countdown: Countdown = _

  @throws(classOf[Exception])
  override def preStart(): Unit = {
    configStore = ConfigStore(fileName)
  }

  def receive: Actor.Receive = {

    case Ready(value) =>
      createComponent()
      countdown = Countdown(configStore.components.size)

    case Acknowledge(value) =>
      countdown.inc
      if (countdown.reset) {
        configStore.components foreach {case (key,ref) => ref ! Ready(key)}
      }
  }

  private def createComponent() = {
    configStore.addComponent("store", context.actorOf(Props[Store]))
    configStore.addComponent("processor", context.actorOf(Props[Processor]))
    configStore.addComponent("messageBuilder", context.actorOf(Props[MessageBuilder]))
    configStore.addComponent("notifier", context.actorOf(Props[Notifier]))

    configStore.components.values foreach {ref => ref ! Config(configStore)}
  }
}

/**
 * Message case classes
 */

case class Ready(value: String)

case class Notify(user: User, message: Option[String])

case class Acknowledge(value: String)

case class Config(configStore: ConfigStore)

case class Build(result: Option[Result], trigger: Trigger)

case class Retrieve(dataType: DataType)

case class Evaluate(trigger: Trigger)

case class RegisterTrigger(trigger: Trigger)

case class Repeat(value: String)