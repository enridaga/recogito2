package models.place.stream

import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.test._
import play.api.test.Helpers._
import play.api.libs.json.Json
import scala.io.Source

@RunWith(classOf[JUnitRunner])
class StreamableRecordSpec extends Specification {
  
  val input = new GZIPInputStream(new FileInputStream("test/resources/models/place/stream/cities-sample.jsonl.gz"))
  
  val records = Source.fromInputStream(input).getLines()
  
  "All test records" should {
    "be successfully parsed from JSON" in {
      val parsed = records.map { record => Json.fromJson[StreamableRecord](Json.parse(record)) }
      parsed.find(_.isError) must equalTo(None)
    }
  }
  
}