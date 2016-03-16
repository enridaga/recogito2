package controllers.my.upload.tiling

import akka.actor.Props
import java.io.File
import controllers.my.upload.SupervisorActor
import models.generated.tables.records.{ DocumentRecord, DocumentFilepartRecord }
import scala.concurrent.duration.FiniteDuration
import models.content.ContentTypes

private[tiling] 
  class TilingSupervisorActor(document: DocumentRecord, parts: Seq[DocumentFilepartRecord], dir: File, keepalive: FiniteDuration)
  extends SupervisorActor(document, parts, dir, keepalive) {
  
  /** Creates workers for every image upload **/
  override def spawnWorkers(document: DocumentRecord, parts: Seq[DocumentFilepartRecord], dir: File) =
    parts
      .filter(_.getContentType.equals(ContentTypes.IMAGE_UPLOAD.toString))
      .map(p => context.actorOf(Props(classOf[TilingWorkerActor], document, p, dir), name="doc_" + document.getId + "_part" + p.getId))
  
}