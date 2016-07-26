package controllers.my.upload.processing.tiling

import akka.actor.Props

import java.io.File
import controllers.my.upload.processing._
import models.ContentType
import models.generated.tables.records.{ DocumentRecord, DocumentFilepartRecord }
import scala.concurrent.duration.FiniteDuration

private[tiling] class TilingSupervisorActor(document: DocumentRecord, parts: Seq[DocumentFilepartRecord], documentDir: File, keepalive: FiniteDuration)
  extends BaseSupervisorActor(ServiceType.IMAGE_TILING, document, parts, documentDir, keepalive) {
  
  /** Creates workers for every image upload **/
  override def spawnWorkers(document: DocumentRecord, parts: Seq[DocumentFilepartRecord], dir: File) =
    parts
      .filter(_.getContentType.equals(ContentType.IMAGE_UPLOAD.toString))
      .map(p => context.actorOf(Props(classOf[TilingWorkerActor], document, p, dir), name="vips-zoomify-" + document.getId + "-part" + p.getId))
  
}