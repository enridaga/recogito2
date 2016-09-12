package models.place.stream

import com.vividsolutions.jts.geom.{ Coordinate, Geometry }
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

case class BBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double)

object BBox {
  
  implicit val bboxReads: Reads[BBox] = 
    JsPath.read[Seq[Double]].map(arr => BBox(arr(0), arr(1), arr(2), arr(3))) 
  
}

case class NameRecord(
    
  attested: String
  
)

case class StreamableRecord(
    
  title: String,
  
  uri: String,
  
  bbox: BBox,
  
  representativePoint: Option[Coordinate],
  
  description: Option[String],
  
  modernCountry: Option[String],
  
  // geometry: Option[Geometry],
  
  // names: Option[Seq[NameRecord]],
  
  placeTypes: Seq[String]
  
)

object StreamableRecord {
  
  implicit val coordinateReads: Reads[Coordinate] =
    JsPath.read[Seq[Double]].map(arr => new Coordinate(arr(0), arr(1)))
  
  implicit val streamableRecordReads: Reads[StreamableRecord] = (
    (JsPath \ "title").read[String] and
    (JsPath \ "uri").read[String] and
    (JsPath \ "bbox").read[BBox] and
    (JsPath \ "repr_point").readNullable[Coordinate] and
    (JsPath \ "description").readNullable[String] and
    (JsPath \ "country_modern").readNullable[String] and
    // (JsPath \ "names").readNullable[Seq[NameRecord]] and
    (JsPath \ "place_types").read[Seq[String]]
  )(StreamableRecord.apply _)
  
}