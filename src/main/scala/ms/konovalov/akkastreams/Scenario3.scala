package ms.konovalov.akkastreams

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy, SinkShape}

import scala.concurrent.duration.DurationDouble
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class Scenario3(apiA: ApiA, apiB: ApiB, apiC: ApiC)
               (implicit system: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext) {

  private val groupSize = 5
  private val apisCount = 3
  private val timeout = 5 seconds

  /* Source queue to push events from outside */
  private val source = Source.queue[RequestContainer[_, _]](groupSize, OverflowStrategy.backpressure)

  /* Graph that collects events into batches by 5 or with timeout */
  private val batchWithTimeoutGraph = Sink.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val bcast = builder.add(Broadcast[RequestContainer[_, _]](apisCount))

    val flowA = apiFlowWithTimeout[RequestA, ResponseA]
    val flowB = apiFlowWithTimeout[RequestB, ResponseB]
    val flowC = apiFlowWithTimeout[RequestC, ResponseC]

    bcast ~> flowA ~> apiA.flowCall() ~> Sink.ignore
    bcast ~> flowB ~> apiB.flowCall() ~> Sink.ignore
    bcast ~> flowC ~> apiC.flowCall() ~> Sink.ignore
    SinkShape(bcast.in)
  })

  /*
   * Generate flow for exact API - filters events by type and group into buckets by 5 or by timeout
   * Doesn't emit events if bucket is empty
   */
  private def apiFlowWithTimeout[Req, Res](implicit tag: ClassTag[Req]) = {
    Flow[RequestContainer[_, _]]
      .filter(f => filterType(f.request))
      .map(_.asInstanceOf[RequestContainer[Req, Res]])
      .groupedWithin(groupSize, timeout)
  }

  /* Queue to push requests for batch with timeout graph */
  private val batchWithTimeoutSourceQueue = source.toMat(batchWithTimeoutGraph)(Keep.left).run()

  /**
    * Method collects requests into batches by 5 or timeout and then makes call to corresponding API
    *
    * @param request API request
    * @param ec      execution context
    * @tparam T type of request identifier
    * @tparam R type of response from API
    * @return Future that will complete when request to API will be made
    */
  def batchedWithTimeoutCall[T, R](request: ApiRequest[T])(implicit ec: ExecutionContext): Future[ApiResponse[R]] =
    offerToQueue(request, batchWithTimeoutSourceQueue)

}
