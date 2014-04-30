package antero.channel

import antero.system._
import antero.system.Result
import antero.utils.Utils._
import scala.io.Source
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json4s.native.JsonMethods._
import org.json4s._
import scala.Some
import scala.Predef._
import scala.Some
import spray.http.{HttpResponse, HttpRequest}
import scala.concurrent.Future
import spray.client.pipelining._


trait WeatherApi {
  val WeatherApiUrl = "http://api.wunderground.com/api"
  val ConditionMethodCall = "conditions/q"
}

/**
 * Created by tungtt on 3/15/14.
 */
class WeatherChannel extends Channel {
  var configStore: ConfigStore = _
  var eventMap: Map[String, Event[String]] = _

  def setConfig(configStore: ConfigStore): Unit = {
    this.configStore = configStore
    eventMap =
      Map("weather.cold" ->
        new Event("weather.cold",
        "Cold Condition Alert",
        60000,
        this,
        TemperaturePredicate(configStore),
        "Current temperature at zipcode $zipCode is $$, lower than $temp"))
  }

  def id = "weather"
  def name = "Weather Channel"
  def events = eventMap.values.toList
  def event(id: String) = eventMap.getOrElse(id, NonEvent)

  def render(event: Event[AnyRef], variables: Map[String, String], result: Result): Option[String] = {
    event.message match {
      case message: String =>
        Some(renderMessage(message, variables, result))

      case _ => None // no message template
    }
  }
}

/*
class SprayTemperaturePredicate(val configStore: ConfigStore) extends Predicate with WeatherApi {
  val apiKey = configStore.configMap.get("weatherApiKey") getOrElse ""

  def evaluate(context: EvaluationContext): Option[Result] = {
    implicit val jsonFormats: Formats = DefaultFormats
    implicit val system = context.actorContext
    import system.dispatcher

    def cal(zipCode: String, temp: Double): Option[Result] = {
      val url = s"$WeatherApiUrl/$apiKey/$ConditionMethodCall/$zipCode.json"

      val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
      val response: Future[HttpResponse] = pipeline(Get(url))

      response.foreach { response =>
        val condition = parse(response.entity.asString).extract[Map[String, Map[String, JValue]]].get("current_observation")
        condition.flatMap(_.get("temp_f")).map(_.extract[Double]).filter(_ < temp).map(Result)
      }
    }

    val result = for {
      zipCode <- context.getVar[String]("zipCode")
      temp <- context.getVar[Double]("temp")
    } yield cal(zipCode, temp)

    result.get
  }
}

object SprayTemperaturePredicate {
  var predicate: SprayTemperaturePredicate = _

  def apply(configStore: ConfigStore): SprayTemperaturePredicate = {
    if (predicate == null)
      predicate = new SprayTemperaturePredicate(configStore)
    predicate
  }
}
*/
class TemperaturePredicate(val configStore: ConfigStore) extends Predicate with WeatherApi {
  val apiKey = configStore.configMap.get("weatherApiKey") getOrElse ""

  def evaluate(context: EvaluationContext): Option[Result] = {
    implicit val jsonFormats: Formats = DefaultFormats

    def cal(zipCode: String, temp: Double): Option[Result] = {
      val url = new URL(s"$WeatherApiUrl/$apiKey/$ConditionMethodCall/$zipCode.json")

      val response = Source.fromURL(url, StandardCharsets.UTF_8.name()).mkString
      val condition = parse(response).extract[Map[String, Map[String, JValue]]].get("current_observation")

      condition.flatMap(_.get("temp_f")).map(_.extract[Double]).filter(_ < temp).map(Result)
    }

    val result = for {
      zipCode <- context.getVar[String]("zipCode")
      temp <- context.getVar[Double]("temp")
    } yield cal(zipCode, temp)

    result.get
  }
}

object TemperaturePredicate {
  var predicate: TemperaturePredicate = _

  def apply(configStore: ConfigStore): TemperaturePredicate = {
    if (predicate == null)
      predicate = new TemperaturePredicate(configStore)
    predicate
  }
}