package example

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.kafka.ProducerSettings
import akka.stream.Attributes
import akka.stream.scaladsl.{ Flow, Source }
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer

import scala.util.{ Failure, Success }

object Producer extends App {

  implicit private val system: ActorSystem = ActorSystem()

  import system.dispatcher

  private val config           = system.settings.config
  private val bootstrapServers = config.getString("bootstrap-servers")
  private val topic            = config.getString("topic")
  private val producerConfig   = config.getConfig("akka.kafka.producer")

  private val producerSettings =
    ProducerSettings(producerConfig, new StringSerializer, new StringSerializer)
      .withBootstrapServers(bootstrapServers)

  /** Sends a batch of elements in burst, and then waits the remainder of the batch interval
    */
  private val sendBatchAndWait: Flow[Int, Int, NotUsed] = {
    import scala.jdk.DurationConverters._

    val pConfig = config.getConfig("producer")

    Flow[Int]
      .batch(pConfig.getInt("batch-size"), a => Vector(a))(_ :+ _)
      .throttle(1, pConfig.getDuration("batch-interval").toScala)
      .flatMapConcat(Source(_))
  }

  Source
    .fromIterator(() => Iterator.from(0))
    .via(sendBatchAndWait)
    .log("Producer")
    .withAttributes(Attributes.logLevels(onElement = Logging.InfoLevel))
    .map { value =>
      val key = value % 10 // simple partitioning
      new ProducerRecord(topic, key.toString, value.toString)
    }
    .runWith(akka.kafka.scaladsl.Producer.plainSink(producerSettings))
    .onComplete {
      case Failure(t) =>
        println("Failed stream " + t.getMessage)
        System.exit(1)

      case Success(_) =>
        println("Finished stream")
        System.exit(0)
    }
}
