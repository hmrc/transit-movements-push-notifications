/*
 * Copyright 2023 HM Revenue & Customs
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
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.http.Status.NOT_FOUND
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.transitmovementspushnotifications.base.SpecBase
import uk.gov.hmrc.transitmovementspushnotifications.base.TestActorSystem
import uk.gov.hmrc.transitmovementspushnotifications.config.AppConfig
import uk.gov.hmrc.transitmovementspushnotifications.connectors.PushPullNotificationConnector
import uk.gov.hmrc.transitmovementspushnotifications.generators.ModelGenerators
import uk.gov.hmrc.transitmovementspushnotifications.models.BoxAssociation
import uk.gov.hmrc.transitmovementspushnotifications.models.BoxId
import uk.gov.hmrc.transitmovementspushnotifications.models.MessageId
import uk.gov.hmrc.transitmovementspushnotifications.models.MessageNotification
import uk.gov.hmrc.transitmovementspushnotifications.models.request.BoxAssociationRequest
import uk.gov.hmrc.transitmovementspushnotifications.models.responses.BoxResponse
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.BoxNotFound
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.InvalidBoxId
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.UnexpectedError

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class PushNotificationServiceSpec extends SpecBase with ModelGenerators with TestActorSystem with ScalaCheckDrivenPropertyChecks {

  val maxPayloadSize                    = 80000
  val mockPushPullNotificationConnector = mock[PushPullNotificationConnector]
  private val mockAppConfig             = mock[AppConfig]

  implicit val ec: ExecutionContext = materializer.executionContext
  implicit val hc: HeaderCarrier    = HeaderCarrier()

  val sut = new PushPullNotificationServiceImpl(mockPushPullNotificationConnector, mockAppConfig)

  val emptyBody: JsValue = Json.obj()

  "getBoxId" - {
    "when given a payload with client id and no boxId it returns the default box id" in forAll(
      arbitrary[BoxAssociationRequest].map(
        x => x.copy(boxId = None)
      ),
      arbitrary[BoxResponse]
    ) {
      (boxAssociationRequest, boxResponse) =>
        when(mockPushPullNotificationConnector.getBox(any[String])(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(Future.successful(boxResponse))

        val result = sut.getBoxId(boxAssociationRequest)

        whenReady(result.value) {
          _ mustBe Right(boxResponse.boxId)
        }
    }

    "when an upstream error is returned by the connector it returns a Left" in forAll(
      arbitrary[BoxAssociationRequest].map(
        x => x.copy(boxId = None)
      )
    ) {
      boxAssociationRequest =>
        val exception = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
        when(mockPushPullNotificationConnector.getBox(any[String])(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(Future.failed(exception))

        val result = sut.getBoxId(boxAssociationRequest)

        whenReady(result.value) {
          _ mustBe Left(UnexpectedError(Some(exception)))
        }
    }

    "when given a payload with a valid box id it returns the given box id" in forAll(
      arbitrary[BoxAssociationRequest],
      arbitrary[BoxId],
      Gen.listOf(arbitrary[BoxId].map(BoxResponse(_)))
    ) {
      (boxAssociationRequest, boxId, otherBoxes) =>
        val response: Seq[BoxResponse]     = Seq(BoxResponse(boxId)) ++ otherBoxes
        val boxAssociationRequestWithBoxId = boxAssociationRequest.copy(boxId = Some(boxId))
        when(mockPushPullNotificationConnector.getAllBoxes(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(Future.successful(response))

        val result = sut.getBoxId(boxAssociationRequestWithBoxId)

        whenReady(result.value) {
          _ mustBe Right(boxId)
        }
    }

    "when given a payload with an invalid box id it should return InvalidBoxId" in forAll(
      arbitrary[BoxAssociationRequest],
      arbitrary[BoxId],
      Gen.listOf(arbitrary[BoxId].map(BoxResponse(_)))
    ) {
      (boxAssociationRequest, boxId, otherBoxes) =>
        val boxResponse                           = otherBoxes.filter(_ != BoxResponse(boxId)) // just in case
        val boxAssociationRequestWithInvalidBoxId = boxAssociationRequest.copy(boxId = Some(boxId))
        when(mockPushPullNotificationConnector.getAllBoxes(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(Future.successful(boxResponse))

        val result = sut.getBoxId(boxAssociationRequestWithInvalidBoxId)

        whenReady(result.value) {
          _ mustBe Left(InvalidBoxId)
        }
    }

    "sendPushNotification" - {

      val sampleString = "<ncts:CC007C PhaseID=\"NCTS5.0\" xmlns:ncts=\"http://ncts.dgtaxud.ec\">some payload</ncts:CC007C>)"
      val payload      = Source.single(ByteString(sampleString, StandardCharsets.UTF_8))

      "when given a valid boxId and a valid payload with size less than maximum allowed" - {
        "should return a valid response" in forAll(
          arbitrary[BoxAssociation],
          arbitrary[MessageId]
        ) {
          (boxAssociation, messageId) =>
            when(mockAppConfig.maxPushPullPayloadSize).thenReturn(maxPayloadSize)

            val expectedMessageNotification =
              MessageNotification(
                s"/customs/transits/movements/${boxAssociation.movementType.urlFragment}/${boxAssociation._id.value}/messages/${messageId.value}",
                Some(sampleString)
              )

            when(
              mockPushPullNotificationConnector
                .postNotification(BoxId(eqTo(boxAssociation.boxId.value)), eqTo(expectedMessageNotification))(any[ExecutionContext], any[HeaderCarrier])
            )
              .thenReturn(Future.successful(Right(())))

            val result = sut.sendPushNotification(
              boxAssociation = boxAssociation,
              contentLength = Some((maxPayloadSize - 1).toString),
              messageId = messageId,
              body = payload
            )

            whenReady(result.value) {
              _ mustBe Right(())
            }
        }
      }

      "when given a valid boxId and a valid payload with size greater than maximum allowed" - {
        "should return a valid response" in forAll(
          arbitrary[BoxAssociation],
          arbitrary[MessageId]
        ) {
          (boxAssociation, messageId) =>
            when(mockAppConfig.maxPushPullPayloadSize).thenReturn(maxPayloadSize)

            val expectedMessageNotification =
              MessageNotification(
                s"/customs/transits/movements/${boxAssociation.movementType.urlFragment}/${boxAssociation._id.value}/messages/${messageId.value}",
                None
              )

            when(
              mockPushPullNotificationConnector
                .postNotification(BoxId(eqTo(boxAssociation.boxId.value)), eqTo(expectedMessageNotification))(any[ExecutionContext], any[HeaderCarrier])
            )
              .thenReturn(Future.successful(Right(())))

            val result = sut.sendPushNotification(
              boxAssociation = boxAssociation,
              contentLength = Some((maxPayloadSize + 1).toString),
              messageId = messageId,
              body = payload
            )

            whenReady(result.value) {
              _ mustBe Right((): Unit)
            }
        }
      }

      "when given a boxId that is not in the database" - {
        "should return a not found response" in forAll(
          arbitrary[BoxAssociation],
          arbitrary[MessageId]
        ) {
          (boxAssociation, messageId) =>
            when(mockAppConfig.maxPushPullPayloadSize).thenReturn(maxPayloadSize)

            when(mockPushPullNotificationConnector.postNotification(BoxId(any()), any[MessageNotification])(any[ExecutionContext], any[HeaderCarrier]))
              .thenReturn(Future.successful(Left(UpstreamErrorResponse(boxAssociation.boxId.toString, NOT_FOUND))))

            val result = sut.sendPushNotification(
              boxAssociation = boxAssociation,
              contentLength = Some((maxPayloadSize - 1).toString),
              messageId = messageId,
              body = payload
            )

            whenReady(result.value) {
              _ mustBe Left(BoxNotFound(boxAssociation.boxId.value))
            }
        }
      }

      "when sending a badly formed request" - {
        "should return a response indicating an unexpected error occurred" in forAll(
          arbitrary[BoxAssociation],
          arbitrary[MessageId]
        ) {
          (boxAssociation, messageId) =>
            when(mockAppConfig.maxPushPullPayloadSize).thenReturn(maxPayloadSize)

            val errorResponse = UpstreamErrorResponse(boxAssociation.boxId.toString, BAD_REQUEST)
            when(mockPushPullNotificationConnector.postNotification(BoxId(any()), any[MessageNotification])(any[ExecutionContext], any[HeaderCarrier]))
              .thenReturn(Future.successful(Left(errorResponse)))

            val result = sut.sendPushNotification(
              boxAssociation = boxAssociation,
              contentLength = Some((maxPayloadSize - 1).toString),
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
