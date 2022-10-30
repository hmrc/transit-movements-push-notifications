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

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.http.Status._
import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.transitmovementspushnotifications.base._
import uk.gov.hmrc.transitmovementspushnotifications.config.AppConfig
import uk.gov.hmrc.transitmovementspushnotifications.connectors.PushPullNotificationConnector
import uk.gov.hmrc.transitmovementspushnotifications.generators.ModelGenerators
import uk.gov.hmrc.transitmovementspushnotifications.models._
import uk.gov.hmrc.transitmovementspushnotifications.models.request.BoxAssociationRequest
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.BoxNotFound
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.UnexpectedError

import java.nio.charset.StandardCharsets
import scala.concurrent._

class PushNotificationServiceSpec extends SpecBase with ModelGenerators with TestActorSystem {

  val clientId        = "clientId"
  val boxResponse     = arbitraryBoxResponse.arbitrary.sample.get
  lazy val messageId  = MessageId("message-id-1")
  lazy val movementId = arbitraryMovementId.arbitrary.sample.get
  val maxPayloadSize  = 80000

  val mockPushPullNotificationConnector = mock[PushPullNotificationConnector]
  private val mockAppConfig             = mock[AppConfig]

  implicit val ec: ExecutionContext = materializer.executionContext
  implicit val hc: HeaderCarrier    = HeaderCarrier()

  val sut = new PushPullNotificationServiceImpl(mockPushPullNotificationConnector, mockAppConfig)

  val emptyBody: JsValue = Json.obj()

  val boxAssociationRequestWithoutBoxId: BoxAssociationRequest = BoxAssociationRequest("ID_123", None)

  val boxAssociationRequestWithBoxId: BoxAssociationRequest = BoxAssociationRequest("ID_456", Some(boxResponse.boxId))

  "getBoxId" - {
    "when given a payload with client id and no boxId it returns the default box id" in {

      when(mockPushPullNotificationConnector.getBox(any[String])(any[ExecutionContext], any[HeaderCarrier]))
        .thenReturn(Future.successful(boxResponse))

      val result = sut.getBoxId(boxAssociationRequestWithoutBoxId)

      whenReady(result.value) {
        _ mustBe Right(boxResponse.boxId)
      }
    }

    "when an upstream error is returned by the connector it returns a Left" in {
      val exception = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
      when(mockPushPullNotificationConnector.getBox(any[String])(any[ExecutionContext], any[HeaderCarrier]))
        .thenReturn(Future.failed(exception))

      val result = sut.getBoxId(boxAssociationRequestWithoutBoxId)

      whenReady(result.value) {
        _ mustBe Left(UnexpectedError(Some(exception)))
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

    "sendPushNotification" - {

      val payload = Source.single(ByteString("<CC007>some payload</CC07>)", StandardCharsets.UTF_8))

      "when given a valid boxId and a valid payload with size less than maximum allowed" - {
        "should return a valid response" in {

          when(mockAppConfig.maxPushPullPayloadSize).thenReturn(maxPayloadSize)

          when(mockPushPullNotificationConnector.postNotification(BoxId(any()), any[MessageNotification])(any[ExecutionContext], any[HeaderCarrier]))
            .thenReturn(Future.successful(Right()))

          val result = sut.sendPushNotification(
            boxId = boxResponse.boxId,
            contentLength = Some((maxPayloadSize - 1).toString),
            movementId = movementId,
            messageId = messageId,
            body = payload
          )

          whenReady(result.value) {
            _ mustBe Right((): Unit)
          }
        }
      }

      "when given a valid boxId and a valid payload with size greater than maximum allowed" - {
        "should return a valid response" in {

          when(mockAppConfig.maxPushPullPayloadSize).thenReturn(maxPayloadSize)

          when(mockPushPullNotificationConnector.postNotification(BoxId(any()), any[MessageNotification])(any[ExecutionContext], any[HeaderCarrier]))
            .thenReturn(Future.successful(Right()))

          val result = sut.sendPushNotification(
            boxId = boxResponse.boxId,
            contentLength = Some((maxPayloadSize + 1).toString),
            movementId = movementId,
            messageId = messageId,
            body = payload
          )

          whenReady(result.value) {
            _ mustBe Right((): Unit)
          }
        }
      }

      "when given a boxId that is not in the database" - {
        "should return a not found response" in {

          when(mockAppConfig.maxPushPullPayloadSize).thenReturn(maxPayloadSize)

          when(mockPushPullNotificationConnector.postNotification(BoxId(any()), any[MessageNotification])(any[ExecutionContext], any[HeaderCarrier]))
            .thenReturn(Future.successful(Left(UpstreamErrorResponse(boxResponse.boxId.toString, NOT_FOUND))))

          val result = sut.sendPushNotification(
            boxId = boxResponse.boxId,
            contentLength = Some((maxPayloadSize - 1).toString),
            movementId = movementId,
            messageId = messageId,
            body = payload
          )

          whenReady(result.value) {
            _ mustBe Left(BoxNotFound(boxResponse.boxId.value))
          }
        }
      }

      "when sending a badly formed request" - {
        "should return a response indicating an unexpected error occurred" in {

          when(mockAppConfig.maxPushPullPayloadSize).thenReturn(maxPayloadSize)

          val errorResponse = UpstreamErrorResponse(boxResponse.boxId.toString, BAD_REQUEST)
          when(mockPushPullNotificationConnector.postNotification(BoxId(any()), any[MessageNotification])(any[ExecutionContext], any[HeaderCarrier]))
            .thenReturn(Future.successful(Left(errorResponse)))

          val result = sut.sendPushNotification(
            boxId = boxResponse.boxId,
            contentLength = Some((maxPayloadSize - 1).toString),
            movementId = movementId,
            messageId = messageId,
            body = payload
          )

          whenReady(result.value) {
            _ mustBe Left(UnexpectedError(Some(errorResponse)))
          }
        }
      }

    }

  }
}
