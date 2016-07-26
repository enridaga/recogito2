package controllers.document.downloads.writers

import javax.inject.Inject
import play.api.cache.CacheApi
import storage.DB
import controllers.BaseAuthController
import models.user.Roles._
import models.annotation.AnnotationService
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import models.annotation.AnnotationBody

class CSVWriterController @Inject() (implicit val cache: CacheApi, val db: DB) extends BaseAuthController {
  
  def downloadAnnotations(documentId: String) = AsyncStack(AuthorityKey -> Normal) { implicit request =>
    AnnotationService.findByDocId(documentId).map { annotations =>
      
      val sorted = annotations.map(_._1).sortWith((a, b) =>
        a.anchor.substring(4).toInt < b.anchor.substring(4).toInt)
      
      val csv = sorted.map { a =>
        val placeBody = a.bodies.filter(_.hasType == AnnotationBody.PLACE).headOption.flatMap(_.uri)
        "\"" + a.getQuote.get + "\";\"" + placeBody.getOrElse("") + "\""
      }.mkString("\n")
      
      Ok(csv)
    }
  }
  
}