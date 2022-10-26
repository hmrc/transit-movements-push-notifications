import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString

import java.nio.charset.StandardCharsets
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.xml.NodeSeq

def createStream(string: String): Source[ByteString, _] =
  Source.single(ByteString(string, StandardCharsets.UTF_8))

val payload: NodeSeq = <CC009C><MRN>123456</MRN><MessageSender>sender1</MessageSender></CC009C>

val xml: NodeSeq = <CC007>test</CC007>
val streamA      = createStream("{\"payload\":")
val streamB      = createStream(payload.mkString)
val streamC      = createStream("\"}\"")

implicit val system       = ActorSystem("MaterializingStreams")
implicit val materializer = ActorMaterializer()
import system.dispatcher

val streamABC: Future[String] = streamA
  .concat(streamB)
  .concat(streamC)
  .map(_.utf8String)
  .runWith(Sink.head)

streamABC onComplete {
  case Success(value) => println(value)
  case Failure(ex)    => println(ex)
}
