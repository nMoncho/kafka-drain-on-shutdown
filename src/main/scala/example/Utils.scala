package example

import akka.NotUsed
import akka.kafka.CommitterSettings
import akka.kafka.ConsumerMessage.Committable
import akka.kafka.scaladsl.Committer
import akka.stream.SourceShape
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Merge, Source }

object Utils {

  sealed abstract class OffsetCommitted

  case object OffsetCommitted extends OffsetCommitted

  def committerFlow(settings: CommitterSettings): Flow[Committable, OffsetCommitted, NotUsed] =
    Committer.flow(settings).map { _ =>
      println("Kafka offset committed")
      OffsetCommitted
    }

  /** Run incoming elements through a [[Committer]] Flow, but putting them back on the stream for
   * downstream processing.
   */
  def mergeAndIntegrate[Out, Mat, FlowIn, FlowOut](source: Source[Out, Mat])(
      toFlowIn: Out => FlowIn
  )(flow: Flow[FlowIn, FlowOut, _]): Source[Either[FlowOut, Out], Mat] =
    Source.fromGraph(GraphDSL.createGraph(source) {
      implicit builder: GraphDSL.Builder[Mat] => originalSource =>
        import GraphDSL.Implicits._

        val broadcast = builder.add(Broadcast[Out](2))
        val merge     = builder.add(Merge[Either[FlowOut, Out]](2))

        val left = Flow[Out]
          .map(toFlowIn)
          .via(flow)
          .map(Left(_))

        val right = Flow[Out]
          .map(Right(_))

        originalSource ~> broadcast.in
        broadcast ~> left ~> merge
        broadcast ~> right ~> merge

        SourceShape(merge.out)
    })

  implicit class CommittableSourceOps[T, Mat](private val source: Source[(T, Committable), Mat])
      extends AnyVal {

    /** Run incoming elements through a [[Committer]] Flow, but putting them back on the stream for
     * downstream processing.
     */
    def commitSource(settings: CommitterSettings): Source[Either[OffsetCommitted, T], Mat] =
      mergeAndIntegrate(source)(_._2)(committerFlow(settings))
        .map(_.map { case (t, _) => t }) // after this point, we don't need `Committable` anymore
  }
}
