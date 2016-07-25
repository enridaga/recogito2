package controllers.my.upload.georesolution

import java.util.UUID
import scala.concurrent.Future
import models.annotation._
import models.place.PlaceService
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import org.joda.time.DateTime

// TODO extend with optional coordinates later
case class Toponym(chars: String)

object GeoresolutionService {
  
  private def toAnnotation(annotatedObject: AnnotatedObject, anchor: String, quote: String, uri: Option[String] = None): Annotation = {
    val now = DateTime.now
    
    Annotation(
      UUID.randomUUID,
      UUID.randomUUID,
      annotatedObject,
      Seq.empty[String], // No contributing users
      anchor,
      None, // no last modifying user
      now,
      Seq(
        AnnotationBody(
          AnnotationBody.QUOTE,
          None,  // no last modifying user
          now,
          Some(quote),
          None,  // uri
          None), // status
        AnnotationBody(
          AnnotationBody.PLACE,
          None,
          now,
          None,
          uri,
          Some(AnnotationStatus(
            AnnotationStatus.UNVERIFIED,
            None,
            now)))))
  }
  
  def resolve(annotatedObject: AnnotatedObject, toponyms: Seq[Toponym]) =
    toponyms.foldLeft(Future.successful(Seq.empty[Annotation])) { case (future, toponym) => 
      future.flatMap { annotations =>
        PlaceService.searchPlaces(toponym.chars, 0, 1).map { topHits =>
          if (topHits.total > 0)
            // TODO be smarter about choosing the right URI from the place
            // TODO how to deal with anchors?
            toAnnotation(annotatedObject, "", toponym.chars, Some(topHits.items(0)._1.id))
          else
            // No gazetteer match found
            toAnnotation(annotatedObject, "",  toponym.chars)
        } map { annotation =>
          annotations :+ annotation
        }
      }
    }
  
  
}