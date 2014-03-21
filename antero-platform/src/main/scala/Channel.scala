package antero.channel

import antero.processor.EvalContext
import antero.system.{MessageTemplate, Result, Predicate}
import scala.concurrent.Future

/**
 * Created by tungtt on 3/11/14.
 */
abstract class Channel {

  // the default predicate always returns false
  val defaultPredicate = new Predicate {
    def evaluate(context: EvalContext): Option[Result] = Some(new Result(true, 0))
  }

  val defaultMessageTemplate = new MessageTemplate {
    def output(args: Map[String, String], result: Result): String = ""
  }

  def predicates: Map[String,Predicate]

  def messageTemplates: Map[String,MessageTemplate]

  def predicate(name: String): Predicate = {
    predicates.get(name).getOrElse(defaultPredicate)
  }

  def messageTemplate(name: String): MessageTemplate = {
    messageTemplates.get(name).getOrElse(defaultMessageTemplate)
  }
}