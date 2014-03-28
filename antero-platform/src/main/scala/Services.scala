import antero.system.{Trigger, Device}

/**
 * Created by tungtt on 3/24/14.
 */

trait ChannelService {
/*
  getPredicateList
  getPredicate(predicateId)
  search
  subscribe(userId, predicateId)
*/
}

trait UserService {
  /**
   *
   * @param userId
   * @return
   */
  def getDevices(userId: String): Seq[Device]

  /**
   *
   * @param userId
   * @return
   */
  def getTriggers(userId: String): Seq[Trigger]

  /**
   *
   * @param registrationId
   * @return
   */
  def registerDevice(registrationId: String): Device

}
