/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
