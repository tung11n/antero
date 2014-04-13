package antero.channel

import antero.system._
import antero.utils.Utils._
import scala.io.Source
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json4s.native.JsonMethods._
import org.json4s._
import scala.Some

object WeatherApi {
  val WundergroundUrl = "http://api.wunderground.com/api/"
  val ConditionMethodCall = "/conditions/q/"
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
      case message: String => Some(renderMessage(message, variables, result))
      case _ => None // no message template
    }
  }
}

class TemperaturePredicate(val configStore: ConfigStore) extends Predicate {
  val apiKey = configStore.configMap.get("weatherApiKey") getOrElse ""

  def evaluate(context: EvaluationContext): Option[Result] = {

    implicit val jsonFormats: Formats = DefaultFormats

    val zipCode = context.getVar[String]("zipCode")
    if (zipCode.isEmpty)
      throw new RuntimeException("zip code not available")

    //val log = context.log
    val url = new URL(WeatherApi.WundergroundUrl + apiKey + WeatherApi.ConditionMethodCall + zipCode + ".json")

    val response = Source.fromURL(url, StandardCharsets.UTF_8.name()).mkString
    val condition = parse(response).extract[Map[String, Map[String, JValue]]].get("current_observation")
    val currentTemp = condition.flatMap(o => Some(o.get("temp_f"))).flatMap(t=>t).getOrElse(JDouble(9999)).extract[Double]

    context.log.info(f"URL $url%s. Current temp: $currentTemp%f")
    context.getVar[Double]("temp").filter(_ > currentTemp).map(_ => new Result(currentTemp))
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