package uk.gov.hmrc.transitmovementspushnotifications.connectors

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.lemonlabs.uri.QueryString
import io.lemonlabs.uri.Url
import io.lemonlabs.uri.UrlPath
import play.api.libs.json.JsResult
import play.api.libs.json.Reads
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.transitmovementspushnotifications.config.Constants

import scala.concurrent.Future

trait BaseConnector {

  val getAllBoxesRoute = UrlPath.parse("/cmb/box")

  def getBoxRoute(clientId: String): UrlPath = Url(path = "box", query = QueryString.fromPairs(("boxName", Constants.BoxName), ("clientId", clientId))).path

  implicit class HttpResponseHelpers(response: HttpResponse) {

    def as[A](implicit reads: Reads[A]): Future[A] =
      response.json
        .validate[A]
        .map(
          result => Future.successful(result)
        )
        .recoverTotal(
          error => Future.failed(JsResult.Exception(error))
        )

    def error[A]: Future[A] =
      Future.failed(UpstreamErrorResponse(response.body, response.status))

  }

}
