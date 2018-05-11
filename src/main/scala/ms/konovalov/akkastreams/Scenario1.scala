package ms.konovalov.akkastreams

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContext, Future}

class Scenario1(apiA: ApiA, apiB: ApiB, apiC: ApiC)
               (implicit system: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext) {

  def makeCalls(calls: Seq[ApiRequest[_]]): Future[Seq[ApiResponse[_]]] = {
    Future.sequence(
      calls.map(call => makeCall(call))
    )
  }

  def makeCall(request: ApiRequest[_]): Future[ApiResponse[_]] = request match {
    case r: RequestA => makeSingleApiCall(apiA, r)
    case r: RequestB => makeSingleApiCall(apiB, r)
    case r: RequestC => makeSingleApiCall(apiC, r)
  }

  private def makeSingleApiCall[ID, Req <: ApiRequest[ID], R, Res <: ApiResponse[R]]
  (api: Api[ID, R, Req, Res], request: Req): Future[Res] =
    api.call(Seq(request)).map(result => result.head)
}
