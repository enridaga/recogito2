package controllers.my.upload

import akka.actor.ActorSystem
import controllers.WebJarAssets
import controllers.BaseAuthController
import controllers.my.upload.processing._
import java.io.File
import java.util.UUID
import javax.inject.Inject
import models.ContentType
import models.ContentIdentificationFailures._
import models.document.DocumentService
import models.upload.UploadService
import models.generated.tables.records.UploadRecord
import models.user.Roles._
import play.api.Play.current
import play.api.Logger
import play.api.cache.CacheApi
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages.Implicits._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.implicitConversions
import storage.DB
import controllers.my.upload.processing.ProcessingServiceRegistry
import controllers.my.upload.processing.ner.NERService
import controllers.my.upload.processing.tiling.TilingService
import controllers.my.upload.processing.ProcessingTaskMessages.WorkerProgress
import controllers.my.upload.processing.ProcessingTaskMessages.DocumentProgress

case class UploadSuccess(contentType: String)

case class NewDocumentData(title: String, author: String, dateFreeform: String, description: String, language: String, source: String, edition: String)

class UploadController @Inject() (implicit val cache: CacheApi, val db: DB, system: ActorSystem, webjars: WebJarAssets) extends BaseAuthController {

  private val FILE_ARG = "file"

  private val MSG_ERROR = "There was an error processing your data"

  val newDocumentForm = Form(
    mapping(
      "title" -> nonEmptyText,
      "author" -> text,
      "date_freeform" -> text,
      "description" -> text,
      "language" -> text,
      "source" -> text,
      "edition" -> text
    )(NewDocumentData.apply)(NewDocumentData.unapply)
  )

  implicit def uploadRecordToNewDocumentData(r: UploadRecord) =
    NewDocumentData(r.getTitle, r.getAuthor, r.getDateFreeform, r.getDescription, r.getLanguage, r.getSource, r.getEdition)


  def showStep1(usernameInPath: String) = AsyncStack(AuthorityKey -> Normal) { implicit request =>
    UploadService.findPendingUpload(loggedIn.user.getUsername).map(_ match {
      case Some(pendingUpload) =>
        Ok(views.html.my.upload.step1(usernameInPath, newDocumentForm.fill(pendingUpload)))

      case None =>
        Ok(views.html.my.upload.step1(usernameInPath, newDocumentForm))
    })
  }

  /** Stores document metadata following step 1 **/
  def storeDocumentMetadata(usernameInPath: String) = AsyncStack(AuthorityKey -> Normal) { implicit request =>
    newDocumentForm.bindFromRequest.fold(
      formWithErrors =>
        Future.successful(BadRequest(views.html.my.upload.step1(usernameInPath, formWithErrors))),

      docData =>
        UploadService.storePendingUpload(loggedIn.user.getUsername, docData.title, docData.author, docData.dateFreeform, docData.description, docData.language, docData.source, docData.edition)
          .flatMap(user => Future.successful(Redirect(controllers.my.upload.routes.UploadController.showStep2(usernameInPath))))
          .recover { case t: Throwable => {
            t.printStackTrace()
            Ok(views.html.my.upload.step1(usernameInPath, newDocumentForm.bindFromRequest, Some(MSG_ERROR)))
          }}
    )
  }

  /** Step 2 requires that a pending upload exists - otherwise, redirect to step 1 **/
  def showStep2(usernameInPath: String) = AsyncStack(AuthorityKey -> Normal) { implicit request =>
    UploadService.findPendingUploadWithFileparts(loggedIn.user.getUsername).map(_ match {
      case Some((pendingUpload, fileparts)) =>
        Ok(views.html.my.upload.step2(usernameInPath, fileparts))

      case None =>
        Redirect(controllers.my.upload.routes.UploadController.showStep1(usernameInPath))
    })
  }

  /** Stores a filepart during step 2 **/
  def storeFilepart(usernameInPath: String) = AsyncStack(AuthorityKey -> Normal) { implicit request =>

    import UploadController._

    // First, we need to get the pending upload this filepart belongs to
    val username = loggedIn.user.getUsername
    UploadService.findPendingUpload(username)
      .flatMap(_ match {
        case Some(pendingUpload) =>
          request.body.asMultipartFormData.map(tempfile => {
            tempfile.file(FILE_ARG).map(f => {
              UploadService.insertFilepart(pendingUpload.getId, username, f).map(_ match {
                case Right(filepart) =>
                  // Upload was properly identified and stored
                  Ok(Json.toJson(UploadSuccess(filepart.getContentType)))

                case Left(UnsupportedContentType) =>
                  BadRequest("Unknown or unsupported file format")

                case Left(otherFailure) =>
                  // For future use
                  BadRequest(MSG_ERROR)
              })
            }).getOrElse({
              // POST without a file? Not possible through the UI!
              Logger.warn("Filepart POST without file attached")
              Future.successful(BadRequest(MSG_ERROR))
            })
          }).getOrElse({
            // POST without form data? Not possible through the UI!
            Logger.warn("Filepart POST without form data")
            Future.successful(BadRequest(MSG_ERROR))
          })

        case None => {
          // No pending upload stored in database? Not possible through the UI!
          Logger.warn("Filepart POST without pending upload")
          Future.successful(BadRequest(MSG_ERROR))
        }
      })
      .recover { case t: Throwable =>
        t.printStackTrace()
        BadRequest(MSG_ERROR)
      }
  }

  /** Deletes a filepart during step 2 **/
  def deleteFilepart(usernameInPath: String, filename: String) = AsyncStack(AuthorityKey -> Normal) { implicit request =>
    UploadService.deleteFilepartByTitleAndOwner(filename, loggedIn.user.getUsername).map(success => {
      if (success) Ok("ok.") else NotFoundPage
    })
  }

  /** Step 3 requires that a pending upload and at least one filepart exists - otherwise, redirect **/
  def showStep3(usernameInPath: String) = AsyncStack(AuthorityKey -> Normal) { implicit request =>
    UploadService.findPendingUploadWithFileparts(loggedIn.user.getUsername).flatMap(_ match {
      case Some((pendingUpload, fileparts)) =>
        if (fileparts.isEmpty) {
          // No fileparts - force user to step 2
          Future.successful(Redirect(controllers.my.upload.routes.UploadController.showStep2(usernameInPath)))
        } else {
          // Pending upload + fileparts available - proceed
          UploadService.importPendingUpload(pendingUpload, fileparts).map { case (doc, docParts) => {
            // 'Apply automatic annotation' flag (set by user)
            val applyAutoAnnotation = checkParamValue("auto-annotation", "on")
            
            // Distinct content types in the document
            val contentTypes = docParts.map(_.getContentType).distinct.flatMap(ContentType.withName(_)).toSet
            
            val services = ProcessingServiceRegistry.getServices(contentTypes, applyAutoAnnotation).map { service => 
              service.spawnTask(doc, docParts)
              service.serviceDescription
            }

            Ok(views.html.my.upload.step3(usernameInPath, doc, docParts, services))
          }}
        }

      case None =>
        // No pending upload - force user to step 1
        Future.successful(Redirect(controllers.my.upload.routes.UploadController.showStep1(usernameInPath)))
    })
  }

  def cancelUploadWizard(usernameInPath: String) = AsyncStack(AuthorityKey -> Normal) { implicit request =>
    UploadService
      .deletePendingUpload(loggedIn.user.getUsername)
      .map(success => {
        // TODO add error message if success == false
        Redirect(controllers.my.routes.MyRecogitoController.index(usernameInPath, None))
      })
      .recover{ case t =>
        // TODO add error message
        Redirect(controllers.my.routes.MyRecogitoController.index(usernameInPath, None))
      }
  }

  /** Queries for processing progress on a specific task and document (user needs to be logged in and own the document) **/
  private def queryTaskProgress(username: String, docId: String, service: ProcessingService) = AsyncStack(AuthorityKey -> Normal) { implicit request =>

    import UploadController._

    DocumentService.findById(docId, Some(username)).flatMap(_ match {
      // Make sure only users with read access can see the progress
      case Some((document, accesslevel)) if accesslevel.canRead => {
        service.queryProgress(docId).map(_ match {
          case Some(result) =>
            Ok(Json.toJson(result))
          case None =>
            NotFoundPage
        })
      }

      case Some(document) =>
        // Document exists, but no read permission
        Future.successful(ForbiddenPage)

      case None =>
        Future.successful(NotFoundPage)
    })
  }

  def queryNERProgress(usernameInPath: String, id: String) = queryTaskProgress(usernameInPath, id, NERService)

  def queryTilingProgress(usernameInPath: String, id: String) = queryTaskProgress(usernameInPath, id, TilingService)

}

/** Defines JSON serialization for NER progress messages **/
object UploadController {

  implicit val uploadSuccessWrites: Writes[UploadSuccess] =
    (JsPath \ "content_type").write[String].contramap(_.contentType)

  implicit val progressStatusValueWrites: Writes[ProgressStatus.Value] =
    Writes[ProgressStatus.Value](status => JsString(status.toString))

  implicit val taskTypeWrites: Writes[ServiceType] =
    Writes[ServiceType](s => JsString(s.name))

  implicit val workerProgressWrites: Writes[WorkerProgress] = (
    (JsPath \ "filepart_id").write[UUID] and
    (JsPath \ "status").write[ProgressStatus.Value] and
    (JsPath \ "progress").write[Double]
  )(unlift(WorkerProgress.unapply))

  implicit val documentProgressWrites: Writes[DocumentProgress] = (
    (JsPath \ "document_id").write[String] and
    (JsPath \ "task_name").write[ServiceType] and
    (JsPath \ "progress").write[Seq[WorkerProgress]]
  )(unlift(DocumentProgress.unapply))

}
