package antero.processor

import antero.system._
import antero.system.Acknowledge
import antero.system.Config
import antero.trigger._
import akka.actor.{ActorLogging, ActorRef, Props, Actor}
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import akka.routing.RoundRobinRouter
import akka.pattern.ask
import akka.util.Timeout
import scala.util.{Success, Failure}

/**
 * Created by tungtt on 2/9/14.
 */
class Processor extends Actor with ActorLogging {
  var bucketSize = 1000 * 60
  var supervisors: Map[Int,ActorRef] = Map()
  var numberOfWorkers: Int = 2
  var notifier: ActorRef = _

  def receive: Actor.Receive = {
    case Config(configStore) =>
      val configMap = configStore.configMap
      notifier = configStore.components.getOrElse("notifier", sender)
      numberOfWorkers = configMap.get("processor.workers").map(n => n.toInt).filter(n => n > 0) getOrElse numberOfWorkers
      bucketSize = configMap.get("processor.bucketSize").map(b => b.toInt).filter(b => b > 0) getOrElse bucketSize
      sender ! Acknowledge("store")

//    case Ready(value) =>

    case trigger: Trigger =>
      val b = bucket(trigger.interval)
      supervisors.get(b) match {
        case Some(supervisor) =>
          supervisor ! trigger
        case None =>
          val supervisor = context.actorOf(Props(classOf[Supervisor], notifier, b, numberOfWorkers))
          supervisors += (b -> supervisor)
          supervisor ! trigger
      }
  }

  private def bucket(interval: Int): Int = (interval / bucketSize + 1) * bucketSize
}

/**
 *
 */
class Sandbox {
  def eval(trigger: Trigger): Boolean = {
    val context = EvalContext(trigger.variables)
    trigger.command match {
      case s: SealedTriggerCommand => s.evaluate(context)
      case _ => false
    }
  }
}

/**
 *
 * @param interval
 * @param numberOfWorkers
 */
class Supervisor(notifier: ActorRef, interval: Int, numberOfWorkers: Int) extends Actor with ActorLogging {
  private var tobeEvaluated = List[Trigger]()
  private var router: ActorRef = _

  @throws(classOf[Exception])
  override def preStart(): Unit = {
    log.info(f"Start a supervisor with interval of $interval%d and $numberOfWorkers%d workers")
    router = context.actorOf(Props(classOf[Worker], notifier).withRouter(RoundRobinRouter(numberOfWorkers)))

    context.system.scheduler.schedule(
      Duration.create(1, TimeUnit.MILLISECONDS),
      Duration.create(interval, TimeUnit.MILLISECONDS),
      self,
      "start"
    )(context.system.dispatcher)
  }

  def receive: Actor.Receive = {
    case "start" =>
      tobeEvaluated foreach {trigger => router ! trigger}

    case trigger: Trigger =>
      log.info(s"Adding trigger $trigger")
      tobeEvaluated = trigger :: tobeEvaluated
  }
}

/**
 *
 */
class Worker(notifier: ActorRef) extends Actor with ActorLogging {
  implicit val timeout = Timeout(5, TimeUnit.SECONDS)

  def receive: Actor.Receive = {
    case trigger: Trigger =>
      val sandbox = new Sandbox
      if (sandbox.eval(trigger)) {
        import context.dispatcher

        val f = ask(notifier, Fire(trigger.user, trigger.message))
        f onSuccess {
          case Success(v) => log.info("Message delivered to owner's device(s). MESSAGE-ID=" + v)
          case Failure(e) => log.error("ERROR ", e)
        }
      }
  }
}

/**
 *
 */
class EvalContext {
  private var vars: Map[String,String] = Map()
  def getVar(varName: String): String = {
    vars getOrElse (varName, "")
  }
}

object EvalContext {
  def apply(vars: Map[String,String]) = {
    val context = new EvalContext()
    context.vars = vars
    context
  }
}