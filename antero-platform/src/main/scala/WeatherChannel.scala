package antero.channel.weather

import antero.channel.Channel
import antero.processor.EvalContext
import antero.system.ConfigStore
import antero.trigger.{MessageTemplate, Predicate, Result}
import scala.io.Source
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json4s.native.JsonMethods._
import org.json4s._
import scala.Some

/**
 * Created by tungtt on 3/15/14.
 */
class WeatherChannel(val configStore: ConfigStore) extends Channel {
  val WundergroundUrl = "http://api.wunderground.com/api/"
  val ConditionMethodCall = "/conditions/q/"
  val apiKey = configStore.configMap.get("weatherApiKey") getOrElse ""

  def predicates: Map[String, Predicate] =
    Map("weather.coldAlert" -> new TemperaturePredicate(this))

  def messageTemplates: Map[String, MessageTemplate] = {
    Map("weather.coldWeather" -> new TemperatureMessageTemplate)
  }
}

class TemperaturePredicate(val channel: WeatherChannel) extends Predicate {

  def evaluate(context: EvalContext): Result = {
    implicit val jsonFormats: Formats = DefaultFormats

    val zipCode = context.getVar("zipCode")
    if (zipCode.isEmpty)
      throw new RuntimeException("zip code not available")

    val log = context.log
    val url = new URL(channel.WundergroundUrl + channel.apiKey + channel.ConditionMethodCall + zipCode + ".json")

    val response = Source.fromURL(url, StandardCharsets.UTF_8.name()).mkString
    val condition = parse(response).extract[Map[String, Map[String, JValue]]].get("current_observation")
    val temp = condition.flatMap(o => Some(o.get("temp_f"))).flatMap(t=>t).getOrElse(JDouble(9999)).extract[Double]

    val t = context.getVar("temp").toDouble
    log.info(s"URL $url temp=$temp")
    new Result(t < temp, temp)
  }
}

class TemperatureMessageTemplate extends MessageTemplate {

  def output(args: Map[String, String], result: Result): String = {
    val components: List[Option[String]] =
      Some("Temperature at zip code") :: args.get("zipCode") :: Some("is lower than") :: args.get("temp") :: Some(". It is now") :: Some(result.extract[java.lang.Double].toString) :: List()

    components.flatten mkString " "
  }
}