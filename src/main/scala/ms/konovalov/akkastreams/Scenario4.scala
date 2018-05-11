package ms.konovalov.akkastreams

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContext, Future}

class Scenario4(apiA: ApiA, apiB: ApiB, apiC: ApiC)
               (implicit mat: ActorMaterializer, ec: ExecutionContext, system: ActorSystem) {

  private val scenario1 = new Scenario1(apiA, apiB, apiC)(system, mat, ec)

  def requestData(requests: Seq[ApiRequest[_]]): Future[OveralResponse] = {
    val futures = requests.map(scenario1.makeCall)
    for {
      list <- Future.sequence(futures)
    } yield {
      val result = list.foldLeft(OveralResponse())(OveralResponse.fold)
      result.responseTimes.foreach { case (s, l) => system.log.debug(s"service $s call took $l ms") }
      result.items.foreach(item => system.log.debug(item.toString))
      result
    }
  }

}
