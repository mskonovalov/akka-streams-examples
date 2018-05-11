package ms.konovalov.akkastreams

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class Scenario5(apiA: ApiA, apiB: ApiB, apiC: ApiC)
               (implicit mat: ActorMaterializer, ec: ExecutionContext, system: ActorSystem) {

  private val scenario1 = new Scenario1(apiA, apiB, apiC)(system, mat, ec)

  def requestDataWithTimeLimit(requests: Seq[ApiRequest[_]], timeout: FiniteDuration)(implicit mat: Materializer, ec: ExecutionContext, system: ActorSystem):
  (Future[OveralResponse], Future[OveralResponse]) = {
    val futures = requests.map(scenario1.makeCall)
    val source = futures.map(Source.fromFuture).foldLeft(Source.empty[ApiResponse[_]])((acc, b) => acc.merge(b))
    val fold = Sink.fold[OveralResponse, ApiResponse[_]](OveralResponse())(OveralResponse.fold)
    val toClient = Flow[ApiResponse[_]].takeWithin(timeout).toMat(fold)(Keep.right)
    val continue = Flow[ApiResponse[_]].toMat(fold)(Keep.right)

    source.alsoToMat(toClient)(Keep.right).toMat(continue)(Keep.both).run()
  }
}
