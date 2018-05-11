package ms.konovalov

import akka.stream.QueueOfferResult
import akka.stream.scaladsl.SourceQueueWithComplete

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.reflect.ClassTag

package object akkastreams {

  /**
    * Check whether object is of provided type
    * @param obj object to test
    * @param tag class tag of checked type
    * @tparam T checked type
    * @return true if object is instance of type
    */
  def filterType[T](obj: Any)(implicit tag: ClassTag[T]): Boolean =
    obj match {
      case _: T => true
      case _ => false
    }

  /*
   * Push request to a queue and create empty Promise for the result of the call
   * After getting result from graph promise will be converted into Future
   */
  def offerToQueue[Res, Req](call: ApiRequest[Req], queue: SourceQueueWithComplete[RequestContainer[_, _]])
                        (implicit ec: ExecutionContext): Future[ApiResponse[Res]] = {
    val promise = Promise[ApiResponse[Res]]()
    queue.offer(RequestContainer[ApiRequest[Req], ApiResponse[Res]](call, promise)).flatMap {
      case QueueOfferResult.Enqueued => promise.future
      case QueueOfferResult.Dropped => Future.failed(new RuntimeException("Queue overflowed. Try again later."))
      case QueueOfferResult.Failure(ex) => Future.failed(ex)
      case QueueOfferResult.QueueClosed => Future.failed(new RuntimeException("Queue was closed (pool shut down) while running the request. Try again later."))
    }
  }
}
