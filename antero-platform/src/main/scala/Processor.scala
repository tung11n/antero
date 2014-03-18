package antero.processor

import antero.message.Build
import antero.system._
import antero.system.Acknowledge
import antero.system.Config
import antero.trigger._
import akka.actor.{ActorLogging, ActorRef, Props, Actor}
import scala.concurrent.duration.Duration
import java.util.concurrent.{Executors, TimeUnit}
import akka.routing.RoundRobinRouter
import akka.pattern.ask
import akka.util.Timeout
import scala.util.{Success, Failure}
import scala.concurrent.{ExecutionContext, Future, Await}
import akka.event.LoggingAdapter

/**
 * Created by tungtt on 2/9/14.
 */
class Processor extends Actor with ActorLogging {
  var bucketSize = 1000 * 60
  var supervisors: Map[Int,ActorRef] = Map()
  var numberOfWorkers: Int = 2
  var messageBuilder: ActorRef = _

  def receive: Actor.Receive = {
    case Config(configStore) =>
      val configMap = configStore.configMap

      messageBuilder = configStore.components.getOrElse("messageBuilder", sender)

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
          val supervisor = context.actorOf(Props(classOf[Supervisor], messageBuilder, b, numberOfWorkers))
          supervisors += (b -> supervisor)
          supervisor ! trigger
      }
  }

  private def bucket(interval: Int): Int = (interval / bucketSize + 1) * bucketSize
}

/**
 *
 * @param interval
 * @param numberOfWorkers
 */
class Supervisor(messageBuilder: ActorRef, interval: Int, numberOfWorkers: Int) extends Actor with ActorLogging {
  private var tobeEvaluated = List[Trigger]()
  private var router: ActorRef = _

  @throws(classOf[Exception])
  override def preStart(): Unit = {
    log.info(f"Start a supervisor with interval of $interval%d and $numberOfWorkers%d workers")
    router = context.actorOf(Props(classOf[Worker], messageBuilder).withRouter(RoundRobinRouter(numberOfWorkers)))

    // calls itself every $interval milliseconds
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
      tobeEvaluated = trigger :: tobeEvaluated
  }
}

/**
 *
 */
class Worker(messageBuilder: ActorRef) extends Actor with ActorLogging {
  implicit val timeout = Timeout(5, TimeUnit.SECONDS)
  implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))

  def receive: Actor.Receive = {

    case trigger: Trigger =>

      val cxt = new EvalContext(context.dispatcher, log, trigger.variables)
      //val result = Future { trigger.predicate.evaluate(cxt) }
      val result = trigger.predicate.evaluate(cxt)

      ask(messageBuilder, Build(result, trigger)) onSuccess {
        case Success(v) => log.info("Message are getting built")
        case Failure(e) => log.error("ERROR ", e)
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