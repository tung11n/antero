package antero.system

/**
 * Created by tungtt on 2/19/14.
 */
class Utils {
}

case class Countdown(var count: Int) {
  def inc = {
   count = if (count > 0) count - 1 else count
   count
  }

  def reset: Boolean = count == 0
}
