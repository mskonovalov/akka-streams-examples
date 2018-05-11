package ms.konovalov.akkastreams

import akka.NotUsed
import akka.stream.scaladsl.Flow

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

sealed trait ApiRequest[ID] {
  val id: ID
}

sealed trait ApiResponse[R] {
  val result: R
  val executionTime: Long
}

trait Api[ID, R, Req <: ApiRequest[ID], Res <: ApiResponse[R]] {

  def call(req: Seq[Req]): Future[Seq[Res]]

  protected def tryCall(req: Seq[Req]): Future[Try[Seq[Res]]]

  def flowCall()(implicit ec: ExecutionContext): Flow[Seq[RequestContainer[Req, Res]], Seq[Promise[Res]], NotUsed] = {
    Flow[Seq[RequestContainer[Req, Res]]]
      .mapAsync(4)(request => {
        val promises = request.map(_.result)
        tryCall(request.map(_.request)).map {
          case Success(responses) =>
            responses.zip(promises).map { case (result, p) => p.success(result) }
            promises
          case Failure(e) =>
            promises.foreach(p => p.failure(e))
            promises
        }
      })
  }
}

case class RequestA(id: String) extends ApiRequest[String]

case class RequestB(id: Int) extends ApiRequest[Int]

case class RequestC(id: Int) extends ApiRequest[Int]

case class RequestContainer[Req, Res](request: Req, result: Promise[Res])

case class ResponseA(result: Seq[Int], executionTime: Long) extends ApiResponse[Seq[Int]]

case class ResponseB(result: Float, executionTime: Long) extends ApiResponse[Float]

case class ResponseC(result: String, executionTime: Long) extends ApiResponse[String]

trait ApiA extends Api[String, Seq[Int], RequestA, ResponseA]

trait ApiB extends Api[Int, Float, RequestB, ResponseB]

trait ApiC extends Api[Int, String, RequestC, ResponseC]



case class OveralResponse(responseTimes: Map[String, Long], items: Seq[ApiResponse[_]]) {

  def add(serviceId: String, executionTime: Long, additional: ApiResponse[_]): OveralResponse = {
    OveralResponse(responseTimes + (serviceId -> executionTime), items :+ additional)
  }
}

object OveralResponse {
  def apply(): OveralResponse = OveralResponse(Map.empty, Seq.empty)


  def fold: (OveralResponse, ApiResponse[_]) => OveralResponse =
    (acc: OveralResponse, b: ApiResponse[_]) => acc.add(b.getClass.getName, b.executionTime, b)
}
