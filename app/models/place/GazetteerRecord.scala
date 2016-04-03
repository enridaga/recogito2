package models.place

import com.vividsolutions.jts.geom.{ Coordinate, Geometry }
import models.HasDate
import org.joda.time.{ DateTime, DateTimeZone }
import org.joda.time.format.DateTimeFormat
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

case class GazetteerRecord(  

  uri: String,

  sourceGazetteer: Gazetteer,
  
  lastChangedAt: DateTime,

  title: String,
    
  placeTypes: Seq[String],
    
  descriptions: Seq[Description],
    
  names: Seq[Name],
    
  geometry: Option[Geometry],
    
  representativePoint: Option[Coordinate],
  
  temporalBounds: Option[TemporalBounds],
  
  closeMatches: Seq[String],
    
  exactMatches: Seq[String]
    
) {
  
  // For convenience
  lazy val allMatches = closeMatches ++ exactMatches
  
  /** Returns true if there is a connection between the two records.
    *   
    * A connection can result from one of the following three causes:
    * - this record lists the other record's URI as a close- or exactMatch
    * - the other record lists this record's URI as a close- or exactMatch
    * - both records share at least one close/exactMatch URI
    */
  def isConnectedWith(other: GazetteerRecord): Boolean =
    allMatches.contains(other.uri) ||
    other.allMatches.contains(uri) ||
    allMatches.exists(matchURI => other.allMatches.contains(matchURI))
    
  /** An 'equals' method that ignores the lastChangedAt property **/
  def equalsIgnoreLastChanged(other: GazetteerRecord): Boolean =
    throw new Exception("Implement me!")

}

case class Description(description: String, language: Option[String] = None)

case class Name(name: String, language: Option[String] = None)

case class TemporalBounds(from: DateTime, to: DateTime)

/** JSON (de)serialization **/

object GazetteerRecord extends HasDate {
  
  import Place._
  
  private def fromOptSeq[T](o: Option[Seq[T]]) =
    o.getOrElse(Seq.empty[T])
  
  private def toOptSeq[T](s: Seq[T]) =
    if (s.isEmpty) None else Some(s)
  
  implicit val gazetteerRecordFormat: Format[GazetteerRecord] = (
    (JsPath \ "uri").format[String] and
    (JsPath \ "source_gazetteer").format[Gazetteer] and
    (JsPath \ "last_changed_at").format[DateTime] and
    (JsPath \ "title").format[String] and
    (JsPath \ "place_types").formatNullable[Seq[String]]
      .inmap[Seq[String]](fromOptSeq[String], toOptSeq[String]) and
    (JsPath \ "descriptions").formatNullable[Seq[Description]]
      .inmap[Seq[Description]](fromOptSeq[Description], toOptSeq[Description]) and
    (JsPath \ "names").formatNullable[Seq[Name]]
      .inmap[Seq[Name]](fromOptSeq[Name], toOptSeq[Name]) and
    (JsPath \ "geometry").formatNullable[Geometry] and
    (JsPath \ "representative_point").formatNullable[Coordinate] and
    (JsPath \ "temporal_bounds").formatNullable[TemporalBounds] and
    (JsPath \ "close_matches").formatNullable[Seq[String]]
      .inmap[Seq[String]](fromOptSeq[String], toOptSeq[String]) and
    (JsPath \ "exact_matches").formatNullable[Seq[String]]
      .inmap[Seq[String]](fromOptSeq[String], toOptSeq[String])
  )(GazetteerRecord.apply, unlift(GazetteerRecord.unapply))
  
}

object Description {
  
  implicit val descriptionFormat: Format[Description] = (
    (JsPath \ "description").format[String] and
    (JsPath \ "language").formatNullable[String]
  )(Description.apply, unlift(Description.unapply))
  
}

object Name {
  
  implicit val literalFormat: Format[Name] = (
    (JsPath \ "name").format[String] and
    (JsPath \ "language").formatNullable[String]
  )(Name.apply, unlift(Name.unapply))
  
}


object TemporalBounds {
  
  private val dateFormatter = DateTimeFormat.forPattern("yyyy-MM-dd").withZone(DateTimeZone.UTC)

  implicit val dateFormat =
    Format(
      JsPath.read[JsString].map { json =>
        dateFormatter.parseDateTime(json.value)
      },
      
      Writes[DateTime] { dt =>
        Json.toJson(dateFormatter.print(dt))
      }
    )
 
  /** Helper to produce a DateTime from a JsValue that's either an Int or a date string **/
  private def flexDateRead(json: JsValue): DateTime =
    json.asOpt[Int] match {
      case Some(year) => {
        new DateTime(DateTimeZone.UTC)
          .withDate(year, 1, 1)
          .withTime(0, 0, 0, 0)
      }
      case None => Json.fromJson[DateTime](json).get
    }
  
  /** Vice versa, generates an Int if the date is a year **/
  private def flexDateWrite(dt: DateTime): JsValue =
    if (dt.monthOfYear == 1 && dt.dayOfMonth == 1 && dt.minuteOfDay == 0)
      Json.toJson(dt.year.get)
    else
      Json.toJson(dt)
          
  implicit val temporalBoundsFormat: Format[TemporalBounds] = (
    (JsPath \ "from").format[JsValue].inmap[DateTime](flexDateRead, flexDateWrite) and
    (JsPath \ "to").format[JsValue].inmap[DateTime](flexDateRead, flexDateWrite)
  )(TemporalBounds.apply, unlift(TemporalBounds.unapply))
  
  def computeUnion(bounds: Seq[TemporalBounds]): TemporalBounds = {
    val from = bounds.map(_.from.getMillis).min
    val to = bounds.map(_.to.getMillis).max
    TemporalBounds(
      new DateTime(from, DateTimeZone.UTC), 
      new DateTime(to, DateTimeZone.UTC))
  }
  
  def fromYears(from: Int, to: Int): TemporalBounds = {
    val f = new DateTime(DateTimeZone.UTC).withDate(from, 1, 1).withTime(0, 0, 0, 0)
    val t = new DateTime(DateTimeZone.UTC).withDate(to, 1, 1).withTime(0, 0, 0, 0)
    TemporalBounds(f, t)
  }
  
}