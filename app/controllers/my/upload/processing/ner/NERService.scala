package controllers.my.upload.processing.ner

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.pattern.ask
import akka.util.Timeout
import controllers.my.upload.processing._
import edu.stanford.nlp.ling.CoreAnnotations
import edu.stanford.nlp.pipeline.{ Annotation, StanfordCoreNLP }
import java.io.File
import java.util.Properties
import models.ContentType
import models.generated.tables.records.{ DocumentRecord, DocumentFilepartRecord }
import play.api.Logger
import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.ClassTag

private[ner] case class Phrase(chars: String, entityTag: String, charOffset: Int)

object NERService extends ProcessingService {

  private lazy val props = new Properties()
  props.put("annotators", "tokenize, ssplit, pos, lemma, ner")

  private var runningPipelines = 0

  private[ner] def parse(text: String)(implicit context: ExecutionContext): Future[Seq[Phrase]] = {
    runningPipelines += 1
    
    if (runningPipelines > 5)
      Logger.warn(runningPipelines + " runnning NER pipelines")
    
    Future {
      scala.concurrent.blocking {
        val document = new Annotation(text)
        val pipeline = new StanfordCoreNLP(props)
        pipeline.annotate(document)
        
        Logger.info("NER annotation completed")
        val phrases = document.get(classOf[CoreAnnotations.SentencesAnnotation]).asScala.toSeq.flatMap(sentence => {
          val tokens = sentence.get(classOf[CoreAnnotations.TokensAnnotation]).asScala.toSeq
          tokens.foldLeft(Seq.empty[Phrase])((result, token) => {
            val entityTag = token.get(classOf[CoreAnnotations.NamedEntityTagAnnotation])
            val chars = token.get(classOf[CoreAnnotations.TextAnnotation])
            val charOffset = token.beginPosition
    
            result.headOption match {
    
              case Some(previousPhrase) if previousPhrase.entityTag == entityTag =>
                // Append to previous phrase if entity tag is the same
                Phrase(previousPhrase.chars + " " + chars, entityTag, previousPhrase.charOffset) +: result.tail
    
              case _ =>
                // Either this is the first token (result.headOption == None), or a new phrase
                Phrase(chars, entityTag, charOffset) +: result
    
            }
          })
        })
    
        runningPipelines -= 1
        
        phrases.filter(_.entityTag != "O")
      }
    }
  }
  
  override val serviceDescription = ProcessingServiceDescription(ServiceType.NER, "stanford-core-nlp", "Named-Entity-Recognition using Stanford CoreNLP library")
    
  override def actorClass[T <: BaseSupervisorActor]: ClassTag[T] = ClassTag(classOf[NERSupervisorActor])
  
  override val supportedContentTypes: Set[ContentType] = Set(ContentType.TEXT_PLAIN)
  
  override val isAnnotationService = true

}
