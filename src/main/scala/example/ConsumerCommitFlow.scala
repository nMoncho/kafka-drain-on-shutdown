package example

import akka.actor.ActorSystem
import akka.event.Logging
import akka.kafka.{ CommitterSettings, ConsumerSettings, Subscriptions }
import akka.kafka.scaladsl.Consumer
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.stream.Attributes
import akka.stream.scaladsl.{ Flow, Sink, Source }
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object ConsumerCommitFlow extends App {
  import Utils._

  implicit private val system: ActorSystem = ActorSystem()

  import system.dispatcher

  private val config                 = system.settings.config
  private val bootstrapServers       = config.getString("bootstrap-servers")
  private val topic                  = config.getString("topic")
  private val consumerConfig         = config.getConfig("consumer")
  private val consumerSettingsConfig = config.getConfig("akka.kafka.consumer")

  private val committerSettings = CommitterSettings.create(
    consumerConfig.withFallback(
      config.getConfig("akka.kafka.committer")
    )
  )

  private val consumerSettings =
    ConsumerSettings(consumerSettingsConfig, new StringDeserializer, new StringDeserializer)
      .withBootstrapServers(bootstrapServers)
      .withGroupId(consumerConfig.getString("group-id"))
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  private val consumeSlowly
      : Flow[Either[OffsetCommitted, String], Either[OffsetCommitted, String], Consumer.Control] =
    Flow[Either[OffsetCommitted, String]]
      .asInstanceOf[Flow[
        Either[OffsetCommitted, String],
        Either[OffsetCommitted, String],
        Consumer.Control
      ]]
      .grouped(committerSettings.maxBatch.toInt)
      .throttle(1, 20.seconds)
      .flatMapConcat(Source(_))

  val control =
    Consumer
      .committableSource(consumerSettings, Subscriptions.topics(topic))
      .map(msg => (msg.record.value(), msg.committableOffset))
      .commitSource(committerSettings)
      // Commits happen in the previous flow, if we consume slowly then we simulate having messages in the
      // stream. Thus we need to keep processing the stream (ie. drain it) on shutdown
      .via(consumeSlowly)
      .log("Consumer")
      .withAttributes(Attributes.logLevels(onElement = Logging.InfoLevel))
      .toMat(Sink.ignore)(DrainingControl.apply)
      .run()

  // It's very important to add a hook that drains the stream on shutdown
  sys.addShutdownHook {
    println("Shutting down stream")

    // Wait until the drain process is finished, otherwise we may miss processing some records
    Await.ready(control.drainAndShutdown(), 10.minutes)

    println("Finished draining")
    ()
  }
}
