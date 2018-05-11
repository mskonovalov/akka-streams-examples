package ms.konovalov.akkastreams

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy, SinkShape}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class Scenario2(apiA: ApiA, apiB: ApiB, apiC: ApiC)
               (implicit system: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext)  {

  private val groupSize = 5
  private val apisCount = 3

  /* Source queue to push events from outside */
  private val source = Source.queue[RequestContainer[_, _]](groupSize, OverflowStrategy.backpressure)

  /* Graph that collects events into batches by 5 */
  private val batchGraph = Sink.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val bcast = builder.add(Broadcast[RequestContainer[_, _]](apisCount))

    val aFlow = apiFlow[RequestA, ResponseA]
    val bFlow = apiFlow[RequestB, ResponseB]
    val cFlow = apiFlow[RequestC, ResponseC]

    bcast ~> aFlow ~> apiA.flowCall() ~> Sink.ignore
    bcast ~> bFlow ~> apiB.flowCall() ~> Sink.ignore
    bcast ~> cFlow ~> apiC.flowCall() ~> Sink.ignore
    SinkShape(bcast.in)
  })

  /*
   * Generate flow for exact api - filters events by type ang group by 5
   */
  private def apiFlow[Req, Res](implicit tag: ClassTag[Req]) = {
    Flow[RequestContainer[_, _]]
      .filter(f => filterType(f.request))
      .map(_.asInstanceOf[RequestContainer[Req, Res]])
      .grouped(groupSize)
  }

  /* Queue to push requests for batch graph */
  private val batchSourceQueue = source.toMat(batchGraph)(Keep.left).run()

  /**
    * Method collects requests into batches by 5 and then makes call to corresponding API
    *
    * @param request API request
    * @param ec      execution context
    * @tparam T type of request identifier
    * @tparam R type of response from API
    * @return Future that will complete when request to API will be made
    */
  def batchedCall[T, R](request: ApiRequest[T])(implicit ec: ExecutionContext): Future[ApiResponse[R]] =
    offerToQueue(request, batchSourceQueue)

}
