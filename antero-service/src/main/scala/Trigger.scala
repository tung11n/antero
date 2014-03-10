package antero.trigger

import antero.processor.EvalContext

/**
 * Created by tungtt on 2/8/14.
 */
trait TriggerCommand

abstract class SealedTriggerCommand extends TriggerCommand {
  def evaluate(context: EvalContext): Boolean
}

class ScriptTriggerCommand(val language: String, val script: String) extends TriggerCommand

class User {
  private var id: Long = _
  private var userName: String = _
  private var devices = List[Device]()
}

trait Message

case class Device(name: String, registerId: String)

case class Trigger(command: TriggerCommand,
                   interval: Int,
                   variables: Map[String, String] = Map(),
                   user: User = null,
                   message: Message = null)