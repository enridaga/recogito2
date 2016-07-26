package controllers.my.upload.processing.georesolution

import controllers.my.upload.processing._
import java.util.UUID
import models.ContentType
import models.annotation._
import models.place.PlaceService
import org.joda.time.DateTime
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future
import scala.reflect.ClassTag
import java.util.regex.Pattern

trait ElasticSearchSanitizer {
  /** Sanitizes special characters and set operators in elastic search search-terms. */
  def sanitize(term: String): String = (
    escapeSpecialCharacters _ andThen
    escapeSetOperators andThen
    collapseWhiteSpaces andThen
    escapeOddQuote
  )(term)

  private def escapeSpecialCharacters(term: String): String = {
    val escapedCharacters = Pattern.quote("""\/+-&|!(){}[]^~*?:""")
    term.replaceAll(s"([$escapedCharacters])", "\\\\$1")
  }

  private def escapeSetOperators(term: String): String = {
    val operators = Set("AND", "OR", "NOT")
    operators.foldLeft(term) { case (accTerm, op) =>
      val escapedOp = escapeEachCharacter(op)
      accTerm.replaceAll(s"""\\b($op)\\b""", escapedOp)
    }
  }

  private def escapeEachCharacter(op: String): String =
    op.toCharArray.map(ch => s"""\\\\$ch""").mkString

  private def collapseWhiteSpaces(term: String): String = term.replaceAll("""\s+""", " ")

  private def escapeOddQuote(term: String): String = {
    if (term.count(_ == '"') % 2 == 1) term.replaceAll("""(.*)"(.*)""", """$1\\"$2""") else term
  }
}

object GeoresolutionService extends ProcessingService with ElasticSearchSanitizer {
  
  private def appendPlaceBody(annotation: Annotation, uri: String): Annotation = {
    val now = DateTime.now
    Annotation(
      annotation.annotationId,
      annotation.versionId,
      annotation.annotates,
      annotation.contributors,
      annotation.anchor,
      None, // no last modifying user
      now,
      annotation.bodies :+  AnnotationBody(
        AnnotationBody.PLACE,
        None,
        now,
        None,
        Some(uri),
        Some(AnnotationStatus(AnnotationStatus.UNVERIFIED, None,now)
      )
    ))
  }
  
  def resolve(annotations: Seq[Annotation]): Future[Seq[Annotation]] =
    resolveAndDisambiguate(annotations.map(a => (a, Option.empty[(Double, Double)])))
   
  def resolveAndDisambiguate(annotations: Seq[(Annotation, Option[(Double, Double)])]): Future[Seq[Annotation]] =    
    annotations.foldLeft(Future.successful(Seq.empty[Annotation])) { case (future, (annotation, maybeCoord)) => 
      future.flatMap { annotations =>
        val fAnnotation = annotation.getQuote match {
          case Some(quote) => PlaceService.searchPlaces(sanitize(quote), 0, 1).map { topHits =>
            if (topHits.total > 0)
              // TODO be smarter about choosing the right URI from the place
              appendPlaceBody(annotation, topHits.items(0)._1.id)
            else
              // No gazetteer match found
              annotation
          }
            
          case None => Future.successful(annotation)
        }

        fAnnotation.map(annotation => annotations :+ annotation)
      }
    }

    
  /*
  def resolveAndDisambiguate(annotations: Seq[(Annotation, Option[(Double, Double)])]): Future[Seq[Annotation]] =    
    annotations.foldLeft(Future.successful(Seq.empty[Annotation])) { case (future, (annotation, maybeCoord)) => 
      future.flatMap { annotations =>
        val fAnnotation = annotation.getQuote match {
          case Some(quote) => PlaceService.searchPlaces(quote, 0, 1).map { topHits =>
            if (topHits.total > 0)
              // TODO be smarter about choosing the right URI from the place
              appendPlaceBody(annotation, topHits.items(0)._1.id)
            else
              // No gazetteer match found
              annotation
          }
            
          case None => Future.successful(annotation)
        }

        fAnnotation.map(annotation => annotations :+ annotation)
      }
    }
    *   def resolveAndDisambiguate(annotations: Seq[(Annotation, Option[(Double, Double)])]): Future[Seq[Annotation]] =    
    annotations.foldLeft(Future.successful(Seq.empty[Annotation])) { case (future, (annotation, maybeCoord)) => 
      future.flatMap { annotations =>
        val fAnnotation = annotation.getQuote match {
          case Some(quote) => PlaceService.searchPlaces(quote, 0, 1).map { topHits =>
            if (topHits.total > 0)
              // TODO be smarter about choosing the right URI from the place
              appendPlaceBody(annotation, topHits.items(0)._1.id)
            else
              // No gazetteer match found
              annotation
          }
            
          case None => Future.successful(annotation)
        }

        fAnnotation.map(annotation => annotations :+ annotation)
      }
    }
    */

  override val serviceDescription = ProcessingServiceDescription(ServiceType.GEO_RESOLUTION, "recogito-idx-resolution", "Geo-resolution via the Recogito gazetteer index")
    
  override def actorClass[T <: BaseSupervisorActor]: ClassTag[T] = ClassTag(classOf[GeoresolutionSupervisorActor])
  
  override val supportedContentTypes: Set[ContentType] = Set(ContentType.DATA_CSV)
  
  override val isAnnotationService = true
  
  
}