package antero.system

/**
 * Created by tungtt on 2/19/14.
 */
object Utils {

  def getStringSetting(varName: String, fallbackValue: String = "")(implicit configMap: Map[String,String]): String = {
    configMap.getOrElse(varName, fallbackValue)
  }

  def getIntSetting(varName: String,fallbackValue: Int = 0)(implicit configMap: Map[String,String]): Int = {
    configMap.get(varName).map(n => n.toInt).filter(n => n > 0) getOrElse fallbackValue
  }
}

case class Countdown(var count: Int) {
  def inc = {
   count = if (count > 0) count - 1 else count
   count
  }

  def reset: Boolean = count == 0
}
