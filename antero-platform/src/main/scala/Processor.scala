package antero.processor

//import antero.processor.Evaluate
import antero.system._
import antero.system.Acknowledge
import antero.system.Build
import antero.system.Config
import antero.system.Notify
import antero.system.Ready
import antero.system.RegisterTrigger
import antero.system.Result
import antero.utils.Conversion._
import akka.actor._
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import akka.pattern.ask
import akka.util.Timeout
import scala.util.{Try, Success, Failure}
import scala.concurrent.{ExecutionContext, Future}
import akka.event.LoggingAdapter
import scala.reflect.runtime.universe._
import akka.routing.RoundRobinPool
import scala.util.Failure
import scala.Some
import scala.util.Success


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

      sender ! Acknowledge("processor")

    case Ready(value) =>

    case RegisterTrigger(trigger) =>
      log.info(s"Registering $trigger")
      val b = bucket(trigger.event.interval)
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

case class Evaluate(trigger: Trigger)
case object Repeat
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
    router = context.actorOf(Props(classOf[Worker], notifier, messageBuilder).withRouter(RoundRobinPool(numberOfWorkers)))

    // calls itself every $interval milliseconds
    context.system.scheduler.schedule(
      Duration.create(1, TimeUnit.MILLISECONDS),
      Duration.create(interval, TimeUnit.MILLISECONDS),
      self,
      Repeat
    )(context.system.dispatcher)
  }

  def receive: Actor.Receive = {
    case Repeat =>
      tobeEvaluated foreach {trigger => router ! Evaluate(trigger)}

    case RegisterTrigger(trigger) =>
      tobeEvaluated = trigger :: tobeEvaluated
  }
}

/**
 * Worker actor
 */
class Worker(notifier: ActorRef, messageBuilder: ActorRef) extends Actor with ActorLogging {
  implicit val timeout = Timeout(10, TimeUnit.SECONDS)
  import context.dispatcher

  def receive: Actor.Receive = {

    case Evaluate(trigger) =>

      val receipt = for {
        result <- Future { evaluate(trigger) }
        message <- ask(messageBuilder, Build(result, trigger)).mapTo[Option[Map[String, String]]]
        notified <- ask(notifier, Notify(trigger.user, message))
      } yield (notified)

      receipt onComplete {
        case Success(v) => log.info(s"User ${trigger.user} notified. Message-ID=$v")
        case Failure(e) => log.info(s"Operation failure $e")
      }
  }

  def evaluate(trigger: Trigger): Option[Result] = {
    try {
      val evaluationContext = new SimpleEvaluationContext(log, trigger.variables, context)
      trigger.event.predicate.evaluate(evaluationContext)
    } catch {
      case e:Exception =>
        log.error(e, "error")
        None
    }
  }
}

/**
 * A simple evaluation context
 */
class SimpleEvaluationContext(val log: LoggingAdapter,
                              val vars: Map[String,String],
                              val actorContext: ActorContext) extends EvaluationContext {

  def getVar[A: TypeTag](varName: String): Option[A] = convertTo(vars)(varName)
}