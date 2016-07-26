package controllers.my.upload.processing.ner

import akka.actor.Props
import controllers.my.upload.processing._
import java.io.File
import models.generated.tables.records.{ DocumentRecord, DocumentFilepartRecord }
import scala.concurrent.duration.FiniteDuration

private[ner]
  class NERSupervisorActor(document: DocumentRecord, parts: Seq[DocumentFilepartRecord], dir: File, keepalive: FiniteDuration) 
  extends BaseSupervisorActor(ServiceType.NER, document, parts, dir, keepalive) {
  
  /** Creates workers for every content type indicated as 'supported' by the Worker class **/
  override def spawnWorkers(document: DocumentRecord, parts: Seq[DocumentFilepartRecord], dir: File) =
    parts
      .filter(part => NERWorkerActor.SUPPORTED_CONTENT_TYPES.contains(part.getContentType))
      .map(p => context.actorOf(Props(classOf[NERWorkerActor], document, p, dir), name="stanford-core-nlp-" + document.getId + "-part" + p.getId))

}


