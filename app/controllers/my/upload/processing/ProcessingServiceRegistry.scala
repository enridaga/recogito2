package controllers.my.upload.processing

import models.ContentType
import controllers.my.upload.processing.georesolution.GeoresolutionService
import controllers.my.upload.processing.ner.NERService
import controllers.my.upload.processing.tiling.TilingService

object ProcessingServiceRegistry {
  
  // Built-in services
  private val registeredServices: Seq[ProcessingService] = Seq(NERService, TilingService, GeoresolutionService)
  
  def getServices(contentTypes: Set[ContentType], autoAnnotate: Boolean): Seq[ProcessingService] =
    registeredServices.filter { service =>
      val matchesContentType = contentTypes.intersect(service.supportedContentTypes).size > 0 
      if (service.isAnnotationService) // Only use annotation services on user request
        matchesContentType && autoAnnotate
      else
        matchesContentType
    }
  
}