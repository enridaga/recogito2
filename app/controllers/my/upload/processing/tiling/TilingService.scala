package controllers.my.upload.processing.tiling

import controllers.my.upload.processing._
import java.io.File
import models.ContentType
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future 
import scala.language.postfixOps
import scala.reflect.ClassTag
import sys.process._

object TilingService extends ProcessingService {
  
  private[tiling] def createZoomify(file: File, destFolder: File): Future[Unit] = {
    Future {
      s"vips dzsave $file $destFolder --layout zoomify" !
    } map { result =>
      if (result == 0)
        Unit
      else
        throw new Exception("Image tiling failed for " + file.getAbsolutePath + " to " + destFolder.getAbsolutePath)
    }
  }
  
  override val serviceDescription = ProcessingServiceDescription(ServiceType.IMAGE_TILING, "vips-zoomify", "Zoomify image tile generation using VIPS")
    
  override def actorClass[T <: BaseSupervisorActor]: ClassTag[T] = ClassTag(classOf[TilingSupervisorActor])
  
  override val supportedContentTypes: Set[ContentType] = Set(ContentType.IMAGE_UPLOAD)
  
  override val isAnnotationService = false
  
}
