package models.place.stream

import java.io.InputStream
import akka.stream.scaladsl._
import models.place.GazetteerRecord
import akka.util.ByteString
import play.api.libs.json.Json
import models.place.PlaceService
import scala.concurrent.ExecutionContext
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.stream.ActorMaterializer
import javax.inject.Inject
import akka.actor.ActorSystem
import akka.stream.ClosedShape
import akka.stream.Materializer

class PlaceStreams @Inject() (implicit materializer: Materializer, ec: ExecutionContext) {
  
  def jsonToPlaces(fis: InputStream) = {
    val source = StreamConverters.fromInputStream(() => fis, 5)
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = Int.MaxValue, allowTruncation = false))
      .map(_.utf8String)
    
    val jsonParser = Flow.fromFunction[String, GazetteerRecord] { str =>
      // TODO replace this with Pleiades GeoJSON format!
      Json.fromJson[GazetteerRecord](Json.parse(str)).get
    }
    
    val batchStream = jsonParser.grouped(200)
    
    val batchImporter = Sink.foreach[Seq[GazetteerRecord]] { records => 
      Await.result(PlaceService.importRecords(records), 1.minute)
    }
        
    val graph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
      
      import GraphDSL.Implicits._
      
      source ~> batchStream ~> batchImporter  
      
      ClosedShape
    })
    
    graph.run()
        
  }
  
}