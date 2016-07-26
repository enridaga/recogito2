package controllers.my.upload.processing

import akka.actor.{ ActorSystem, Props }
import akka.pattern.ask
import akka.util.Timeout
import java.io.File
import models.generated.tables.records.{ DocumentRecord, DocumentFilepartRecord }
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.ClassTag
import storage.FileAccess
import models.ContentType

/** Common 'interface definition' for upload processing services **/

sealed trait ServiceType { def name: String }

object ServiceType {
  
  case object NER            extends ServiceType { val name = "NER" }
  case object GEO_RESOLUTION extends ServiceType { val name = "GEO_RESOLUTION" }
  case object IMAGE_TILING   extends ServiceType { val name = "IMAGE_TILING" }
  
}

case class ProcessingServiceDescription(serviceType: ServiceType, name: String, description: String)

trait ProcessingService extends FileAccess  {
  
  import ProcessingTaskMessages._
    
  def serviceDescription: ProcessingServiceDescription
  
  def actorClass[T <: BaseSupervisorActor]: ClassTag[T]
  
  def supportedContentTypes: Set[ContentType]
  
  def isAnnotationService: Boolean
  
  def spawnTask(document: DocumentRecord, parts: Seq[DocumentFilepartRecord])(implicit system: ActorSystem): Unit =
    spawnTask(document, parts, getDocumentDir(document.getOwner, document.getId).get)

  /** We're splitting this function, so we can inject alternative folders for testing **/
  private[upload] def spawnTask(document: DocumentRecord, parts: Seq[DocumentFilepartRecord], sourceFolder: File, keepalive: Duration = 10.minutes)(implicit system: ActorSystem): Unit = {
    val actor = system.actorOf(Props(actorClass.runtimeClass, document, parts, sourceFolder, keepalive), name = serviceDescription.name + "-" + document.getId)
    actor ! ProcessingTaskMessages.Start
  }
  
  def queryProgress(documentId: String, timeout: FiniteDuration = 10.seconds)(implicit context: ExecutionContext, system: ActorSystem): Future[Option[DocumentProgress]] = {
    ProcessingTaskDirectory.getSupervisorActor(serviceDescription.serviceType, documentId) match {
      case Some(actor) => {
        implicit val t = Timeout(timeout)
        (actor ? ProcessingTaskMessages.QueryProgress).mapTo[ProcessingTaskMessages.DocumentProgress].map(Some(_))
      }

      case None =>
        Future.successful(None)
    }
  }
  
}
