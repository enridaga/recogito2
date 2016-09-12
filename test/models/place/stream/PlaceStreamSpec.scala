package models.place.stream

import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.test._
import play.api.test.Helpers._
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import play.api.Play
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import scala.concurrent.ExecutionContext.Implicits.global

@RunWith(classOf[JUnitRunner])
class PlaceStreamSpec extends Specification {
  
  val input = new GZIPInputStream(new FileInputStream("test/resources/models/place/stream/cities-sample.jsonl.gz"))
  
  implicit val materializer = GuiceApplicationBuilder().build().materializer  
  
  "The place stream" should {
    
    "just work" in {      
      val pipeline = new PlaceStream()
      pipeline.jsonToPlaces(input)
      
      Thread.sleep(10000)
      
      success
    }
    
  }
  
  
}