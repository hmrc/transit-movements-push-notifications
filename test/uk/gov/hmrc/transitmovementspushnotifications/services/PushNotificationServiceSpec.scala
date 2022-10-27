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

package uk.gov.hmrc.transitmovementspushnotifications.services

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.transitmovementspushnotifications.base.SpecBase
import uk.gov.hmrc.transitmovementspushnotifications.base.TestActorSystem
import uk.gov.hmrc.transitmovementspushnotifications.connectors.PushPullNotificationConnector
import uk.gov.hmrc.transitmovementspushnotifications.generators.ModelGenerators
import uk.gov.hmrc.transitmovementspushnotifications.models.request.BoxAssociationRequest
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError.UnexpectedError

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class PushNotificationServiceSpec extends SpecBase with ModelGenerators with TestActorSystem {

  val clientId    = "clientId"
  val boxResponse = arbitraryBoxResponse.arbitrary.sample.get

  val mockPushPullNotificationConnector = mock[PushPullNotificationConnector]

  implicit val ec: ExecutionContext = materializer.executionContext
  implicit val hc: HeaderCarrier    = HeaderCarrier()

  val sut = new PushPullNotificationServiceImpl(mockPushPullNotificationConnector)

  val emptyBody: JsValue = Json.obj()

  val boxAssociationRequestWithoutBoxId: BoxAssociationRequest = BoxAssociationRequest("ID_123", "arrival", None)

  val boxAssociationRequestWithBoxId: BoxAssociationRequest = BoxAssociationRequest("ID_456", "arrival", Some(boxResponse.boxId))

  "getBoxId" - {
    "when given a payload with client id and no boxId it returns the default box id" in {

      when(mockPushPullNotificationConnector.getBox(any[String])(any[ExecutionContext], any[HeaderCarrier]))
        .thenReturn(Future.successful(boxResponse))

      val result = sut.getBoxId(boxAssociationRequestWithoutBoxId)

      whenReady(result.value) {
        r =>
          r.isRight mustBe true
          r mustBe Right(boxResponse.boxId)
      }
    }

    "when an upstream error is returned by the connector it returns a Left" in {
      val exception = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
      when(mockPushPullNotificationConnector.getBox(any[String])(any[ExecutionContext], any[HeaderCarrier]))
        .thenReturn(Future.failed(exception))

      val result = sut.getBoxId(boxAssociationRequestWithoutBoxId)

      whenReady(result.value) {
        r =>
          r mustBe Left(UnexpectedError(Some(exception)))
      }
    }

    "when given a payload with a valid box id it returns the given box id" in {
      when(mockPushPullNotificationConnector.getAllBoxes(any[ExecutionContext], any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(boxResponse)))

      val result = sut.getBoxId(boxAssociationRequestWithBoxId)

      whenReady(result.value) {
        r =>
          r mustBe Right(boxResponse.boxId)
      }
    }

  }
}
