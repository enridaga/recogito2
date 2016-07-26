package controllers.my.upload.processing

import akka.actor.ActorRef

/** Helper class to keep track of all currently active task supervisor actors.
  *
  * Reminder: one supervisor actor is responsible for ONE task currently being
  * performed on ONE document. 
  */
object ProcessingTaskDirectory {
  
  private val supervisors = scala.collection.mutable.Map.empty[(ServiceType, String), ActorRef]

  def registerSupervisorActor(service: ServiceType, id: String, actor: ActorRef) = supervisors.put((service, id), actor)

  def deregisterSupervisorActor(service: ServiceType, id: String) = supervisors.remove((service, id))

  def getSupervisorActor(service: ServiceType, id: String) = supervisors.get((service, id))
  
}