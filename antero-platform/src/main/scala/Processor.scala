package antero.processor

import antero.system._
import antero.system.Acknowledge
import antero.system.Config
import akka.actor.{ActorLogging, ActorRef, Props, Actor}
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import akka.routing.RoundRobinRouter
import akka.pattern.ask
import akka.util.Timeout
import scala.util.{Try, Success, Failure}
import scala.concurrent.{ExecutionContext, Future}
import akka.event.LoggingAdapter

/**
 * Created by tungtt on 2/9/14.
 */
class Processor extends Actor with ActorLogging {
  var bucketSize = 1000 * 60
  var supervisors: Map[Int,ActorRef] = Map()
  var numberOfWorkers: Int = 2
  var messageBuilder: ActorRef = _
  var notifier: ActorRef = _

  def receive: Actor.Receive = {

    case Config(configStore) =>
      messageBuilder = configStore.components.getOrElse("messageBuilder", sender)
      notifier = configStore.components.getOrElse("notifier", sender)

      numberOfWorkers = configStore.getIntSetting("processor.workers", numberOfWorkers)
      bucketSize = configStore.getIntSetting("processor.bucketSize", bucketSize)

      sender ! Acknowledge("store")

    case Ready(value) =>

    case RegisterTrigger(trigger) =>
      val b = bucket(trigger.interval)
      supervisors.get(b) match {
        case Some(supervisor) =>
          supervisor ! RegisterTrigger(trigger)
        case None =>
          val supervisor = context.actorOf(Props(classOf[Supervisor], notifier, messageBuilder, b, numberOfWorkers))
          supervisors += (b -> supervisor)
          supervisor ! RegisterTrigger(trigger)
      }
  }

  private def bucket(interval: Int): Int = (interval / bucketSize + 1) * bucketSize
}

/**
 *
 * @param interval
 * @param numberOfWorkers
 */
class Supervisor(notifier: ActorRef, messageBuilder: ActorRef, interval: Int, numberOfWorkers: Int) extends Actor with ActorLogging {
  private var tobeEvaluated = List[Trigger]()
  private var router: ActorRef = _

  @throws(classOf[Exception])
  override def preStart(): Unit = {
    log.info(f"Start a supervisor with interval of $interval%d and $numberOfWorkers%d workers")
    router = context.actorOf(Props(classOf[Worker], notifier, messageBuilder).withRouter(RoundRobinRouter(numberOfWorkers)))

    // calls itself every $interval milliseconds
    context.system.scheduler.schedule(
      Duration.create(1, TimeUnit.MILLISECONDS),
      Duration.create(interval, TimeUnit.MILLISECONDS),
      self,
      Repeat("start")
    )(context.system.dispatcher)
  }

  def receive: Actor.Receive = {
    case Repeat("start") =>
      tobeEvaluated foreach {trigger => router ! Evaluate(trigger)}

    case RegisterTrigger(trigger) =>
      tobeEvaluated = trigger :: tobeEvaluated
  }
}

/**
 *
 */
class Worker(notifier: ActorRef, messageBuilder: ActorRef) extends Actor with ActorLogging {
  implicit val timeout = Timeout(5, TimeUnit.SECONDS)
  import context.dispatcher

  def receive: Actor.Receive = {

    case Evaluate(trigger) =>

      val receipt = for {
        result <- Future { evaluate(trigger) }
        message <- ask(messageBuilder, Build(result, trigger)).mapTo[Option[String]]
        notified <- ask(notifier, Notify(trigger.user, message))
      } yield (notified)

      receipt onComplete {
        case Success(v) => log.info(s"User ${trigger.user} notified. Message-ID=$v")
        case Failure(e) => log.info(s"Operation failure $e")
      }
  }

  def evaluate(trigger: Trigger): Option[Result] = {
    val evalContext = new EvalContext(context.dispatcher, log, trigger.variables)

    try {
      trigger.predicate.evaluate(evalContext)
    } catch {
      case e:Exception =>
        log.error(e, "error")
        None
    }
  }
}

/**
 *
 */
class EvalContext(val executionContext: ExecutionContext, val log: LoggingAdapter, val vars: Map[String,String]) {
  def getVar(varName: String): String = {
    vars getOrElse (varName, "")
  }
}