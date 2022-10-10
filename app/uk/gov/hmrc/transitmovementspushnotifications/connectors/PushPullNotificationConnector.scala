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

import cats.data.EitherT
import com.google.inject.ImplementedBy
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2
import com.google.inject.Inject
import play.api.http.Status.OK
import uk.gov.hmrc.transitmovementspushnotifications.config.AppConfig
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.transitmovementspushnotifications.models.responses.BoxResponse

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[PushPullNotificationConnectorImpl])
trait PushPullNotificationConnector {

  def getBox(clientId: String)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[BoxResponse]

  def getAllBoxes(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[Seq[BoxResponse]]
}

class PushPullNotificationConnectorImpl @Inject() (appConfig: AppConfig, httpClientV2: HttpClientV2) extends PushPullNotificationConnector with BaseConnector {

  override def getBox(clientId: String)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[BoxResponse] = {

    val url = appConfig.pushPullUrl.withPath(getBoxRoute(clientId))

    httpClientV2
      .get(url"$url")
      .execute[HttpResponse]
      .flatMap {
        response =>
          response.status match {
            case OK => response.as[BoxResponse]
            case _  => response.error
          }
      }
  }

  override def getAllBoxes(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[BoxResponse]] = {

    val url = appConfig.pushPullUrl.withPath(getAllBoxesRoute)

    httpClientV2
      .get(url"$url")
      .execute[HttpResponse]
      .flatMap {
        response =>
          response.status match {
            case OK => response.as[Seq[BoxResponse]]
            case _  => response.error
          }
      }

  }
}
