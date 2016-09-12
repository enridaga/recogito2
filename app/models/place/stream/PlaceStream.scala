package models.place.stream

import akka.stream.{ ClosedShape, Materializer }
import akka.stream.scaladsl._
import akka.util.ByteString
import java.io.InputStream
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import play.api.libs.json.Json

class PlaceStream @Inject() (implicit materializer: Materializer, ctx: ExecutionContext) {
  
  def jsonToPlaces(stream: InputStream) = {
    val source = StreamConverters.fromInputStream(() => stream, 5)
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = Int.MaxValue, allowTruncation = false))
      .map(_.utf8String)
      
    val jsonParser = Flow.fromFunction[String, StreamableRecord] { str =>
      Json.fromJson[StreamableRecord](Json.parse(str)).get
    }
    
    val batchStream = jsonParser.grouped(200)
    
    val batchImporter = Sink.foreach[Seq[StreamableRecord]] { records =>
      play.api.Logger.info("batch!")
    }
    
    val graph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      
      source ~> batchStream ~> batchImporter
      
      ClosedShape
    })
    
    graph.run()
  }
  
}