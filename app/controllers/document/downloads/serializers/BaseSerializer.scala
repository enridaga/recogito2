package controllers.document.downloads.serializers

import models.ContentType
import models.annotation.{ Annotation, AnnotationBody }
import play.api.Logger

trait BaseSerializer {
  
  protected val TMP_DIR = System.getProperty("java.io.tmpdir")

  private def sortByCharOffset(annotations: Seq[Annotation]) = {
    annotations.sortWith { (a, b) =>
      a.anchor.substring(12).toInt < b.anchor.substring(12).toInt
    }
  }

  private def sortByXY(annotations: Seq[Annotation]) = {
    // TODO port nearest-neighbour sorting from Recogito v.1
    annotations
  }

  /** Attempts to sort annotations by a sane mechanism, depending on content type.
    *
    * By and large, we should be dealing with documents where all parts have the same
    * content type - but it's not guaranteed.
    */
  protected def sort(annotations: Seq[Annotation]) = {
    val groupedByContentType = annotations.groupBy(_.annotates.contentType)

    groupedByContentType.flatMap { case (cType, a) => cType match {
      case ContentType.TEXT_PLAIN => sortByCharOffset(a)
      case ContentType.IMAGE_UPLOAD | ContentType.IMAGE_IIIF => sortByXY(a)
      case _ => {
        Logger.warn(s"Can't sort annotations of unsupported content type $cType")
        a
      }
    }}
  }
  
  protected def getFirstQuote(a: Annotation): Option[String] = 
    a.bodies.find(_.hasType == AnnotationBody.QUOTE).flatMap(_.value)

  protected def getFirstTranscription(a: Annotation): Option[String] =
    a.bodies.find(_.hasType == AnnotationBody.TRANSCRIPTION).flatMap(_.value)

  protected def getFirstEntityBody(a: Annotation): Option[AnnotationBody] = {
    import AnnotationBody._
    a.bodies.find(b => b.hasType == PERSON || b.hasType == PLACE )
  }

}