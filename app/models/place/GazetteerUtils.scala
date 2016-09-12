package models.place

import java.io.{ File, FileInputStream, InputStream }
import java.util.zip.GZIPInputStream
import org.joda.time.{ DateTime, DateTimeZone }
import org.pelagios.Scalagios
import org.pelagios.api.PeriodOfTime
import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._

object GazetteerUtils {
  
  /** Normalizes a URI to a standard format
    * 
    * Removes '#this' suffixes (used by Pleiades) and, by convention, trailing slashes. 
    */
  def normalizeURI(uri: String) = {
    val noThis = if (uri.indexOf("#this") > -1) uri.substring(0, uri.indexOf("#this")) else uri
      
    if (noThis.endsWith("/"))
      noThis.substring(0, noThis.size - 1)
    else 
      noThis
  }
  
  /** Returns a clone of the gazetteer record, with all URIs normalized **/
  def normalizeRecord(r: GazetteerRecord) = 
    GazetteerRecord(
      normalizeURI(r.uri),
      r.sourceGazetteer,
      r.lastChangedAt,
      r.title,
      r.placeTypes,
      r.descriptions,
      r.names,
      r.geometry,
      r.representativePoint,
      r.temporalBounds,
      r.closeMatches.map(normalizeURI(_)),
      r.exactMatches.map(normalizeURI(_)))
      
  /** Generates a list of name forms (without language), sorted by frequency of appearance in gazetteer records **/
  def collectLabels(records: Seq[GazetteerRecord]): Seq[String] = {
    val titlesAndNames = 
      records.map(_.title) ++
      records.flatMap(_.names.map(_.name))
      
    titlesAndNames
      .flatMap(_.split(",|/").map(_.trim)) // Separate on commas
      .groupBy(identity).toSeq
      .sortBy(- _._2.size)
      .map(_._1)
  }
  
  private def convertPeriodOfTime(period: PeriodOfTime): TemporalBounds = {
    val startDate = period.start
    val endDate = period.end.getOrElse(startDate)
    
    TemporalBounds(
      new DateTime(startDate).withZone(DateTimeZone.UTC), 
      new DateTime(endDate).withZone(DateTimeZone.UTC))          
  } 
  
  private def toRecord(p: org.pelagios.api.gazetteer.Place, gazetteerName: String) = 
    GazetteerRecord(
      GazetteerUtils.normalizeURI(p.uri),
      Gazetteer(gazetteerName),
      DateTime.now().withZone(DateTimeZone.UTC),
      p.label,
      p.category.map(category => Seq(category.toString)).getOrElse(Seq.empty[String]),
      p.descriptions.map(l => Description(l.chars, l.lang)),
      p.names.map(l => Name(l.chars, l.lang)),
      p.location.map(_.geometry),
      p.location.map(_.pointLocation),
      p.temporalCoverage.map(convertPeriodOfTime(_)),
      p.closeMatches.map(normalizeURI(_)),
      p.exactMatches.map(normalizeURI(_)))   
      
  private def getStream(file: File, filename: String) =
    if (filename.endsWith(".gz"))
      new GZIPInputStream(new FileInputStream(file))
    else
      new FileInputStream(file)
      
  def loadRDF(file: File, filename: String): Seq[GazetteerRecord] = {
    val gazetteerName = filename.substring(0, filename.indexOf('.'))
    val stream = getStream(file, filename)
    loadRDF(stream, filename, gazetteerName)
  }
    
  def loadRDF(is: InputStream, filename: String, gazetteerName: String): Seq[GazetteerRecord] = 
    Scalagios.readPlaces(is, filename).map(p => toRecord(p, gazetteerName)).toSeq
    
  def importRDFStream(file: File, filename: String, placeService: PlaceService)(implicit context: ExecutionContext): Unit = {
    val gazetteerName = filename.substring(0, filename.indexOf('.'))
    val stream = getStream(file, filename)
    
    def placeHandler(p: org.pelagios.api.gazetteer.Place) = {
      Await.result(placeService.importRecord(toRecord(p, gazetteerName)), 10.seconds)
    }
    
    play.api.Logger.info("Importing stream")   
    Scalagios.readPlacesFromStream(stream, Scalagios.guessFormatFromFilename(filename).get, placeHandler, true)
  }

}
