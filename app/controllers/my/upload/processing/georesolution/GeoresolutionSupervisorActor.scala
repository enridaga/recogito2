package controllers.my.upload.processing.georesolution

import akka.actor.Props
import controllers.my.upload.processing._
import java.io.File
import models.ContentType
import models.generated.tables.records.{ DocumentRecord, DocumentFilepartRecord }
import scala.concurrent.duration.FiniteDuration

private[georesolution]
  class GeoresolutionSupervisorActor(document: DocumentRecord, parts: Seq[DocumentFilepartRecord], dir: File, keepalive: FiniteDuration) 
  extends BaseSupervisorActor(ServiceType.GEO_RESOLUTION, document, parts, dir, keepalive) {
  
  /** Creates workers for every content type indicated as 'supported' by the Worker class **/
  override def spawnWorkers(document: DocumentRecord, parts: Seq[DocumentFilepartRecord], dir: File) =
    parts
      .filter(part => part.getContentType == ContentType.DATA_CSV.toString)
      .map(p => context.actorOf(Props(classOf[GeoresolutionWorkerActor], document, p, dir), name="recogito-idx-resolution-" + document.getId + "-part" + p.getId))

}
