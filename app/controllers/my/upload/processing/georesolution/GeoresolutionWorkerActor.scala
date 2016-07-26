package controllers.my.upload.processing.georesolution

import akka.actor.Actor
import com.github.tototoshi.csv.{ CSVReader, DefaultCSVFormat }
import controllers.my.upload.processing.ProgressStatus
import java.io.File
import java.util.UUID
import models.annotation._
import models.generated.tables.records.{ DocumentRecord, DocumentFilepartRecord }
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future
import models.ContentType
import org.joda.time.DateTime
import scala.concurrent.Await
import scala.concurrent.duration._

private[georesolution] class GeoresolutionWorkerActor(document: DocumentRecord, part: DocumentFilepartRecord, documentDir: File) extends Actor {
  
  import controllers.my.upload.processing.ProcessingTaskMessages._
import controllers.my.upload.processing.tiling.TilingService;

  var progress = 0.0
  var status = ProgressStatus.PENDING
  
  def receive = {

    case Start => {
      status = ProgressStatus.IN_PROGRESS
      
      val origSender = sender
      val filename = part.getFilename
      val annotationsWithCoordinates = readTable(new File(documentDir, filename))
      play.api.Logger.info("got " + annotationsWithCoordinates.size + " annotations")
      
      annotationsWithCoordinates.grouped(1000).foreach { group =>
        play.api.Logger.info("resolving " + group.size + " annotations")
        val f = GeoresolutionService.resolveAndDisambiguate(group).map { annotations =>   
          play.api.Logger.info("storing " + annotations.size + " annotations")
          AnnotationService.insertOrUpdateAnnotations(annotations).map { result =>
            play.api.Logger.info("done")
            // progress = 1.0
            // status = ProgressStatus.COMPLETED
            // if (result.size == 0)
              // origSender ! Completed
            // else
              // origSender ! Failed
          }
        }
        
        Await.result(f, 200.seconds)
      }
    }

    case QueryProgress =>
      sender ! WorkerProgress(part.getId, status, progress)

  }
  
  private def readTable(file: File) = {
    
    implicit object SemicolonSeparated extends DefaultCSVFormat {
      override val delimiter = ';'
      override val quoteChar = '"'
    }
      
    val reader = CSVReader.open(file)
    
    def parseNumber(str: String): Option[Double] = try {
      Some(str.toDouble)
    } catch { case t: Throwable => None } 
    
    reader.iteratorWithHeaders.zipWithIndex.toSeq.map { case (row, idx) =>
      val toponym = row.get("normalisiert")
      val lat = row.get("Lat").flatMap(parseNumber(_))
      val lng = row.get("Long").flatMap(parseNumber(_))
      val now = new DateTime
      
      val annotation = Annotation(
        UUID.randomUUID,
        UUID.randomUUID,
        AnnotatedObject(document.getId, part.getId, ContentType.withName(part.getContentType).get),
        Seq.empty[String],
        "row:" + idx,
        None, // no last modifying user
        now,
        Seq(
          AnnotationBody(
            AnnotationBody.QUOTE,
            None,
            now,
            toponym,
            None,
            None))
        )
        

      
      val latLng = if (lat.isDefined && lng.isDefined) Some((lat.get, lng.get)) else None 
      (annotation, latLng)
    }
  }
  
}