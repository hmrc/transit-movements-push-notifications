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

package uk.gov.hmrc.transitmovementspushnotifications.controllers

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.util.Timeout
import cats.data.EitherT
import org.mockito.Mockito.reset
import org.mockito.Mockito.when
import uk.gov.hmrc.http.HttpVerbs.POST
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.ACCEPTED
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.CREATED
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.http.Status.NOT_FOUND
import play.api.http.Status.NO_CONTENT
import play.api.mvc.Request
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.AnyContent
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsJson
import play.api.test.Helpers.contentAsString
import play.api.test.Helpers.status
import play.api.test.Helpers.stubControllerComponents
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpVerbs.PATCH
import uk.gov.hmrc.transitmovementspushnotifications.base.SpecBase
import uk.gov.hmrc.transitmovementspushnotifications.base.TestActorSystem
import uk.gov.hmrc.transitmovementspushnotifications.generators.ModelGenerators
import uk.gov.hmrc.transitmovementspushnotifications.models.BoxAssociation
import uk.gov.hmrc.transitmovementspushnotifications.models.BoxId
import uk.gov.hmrc.transitmovementspushnotifications.models.MessageId
import uk.gov.hmrc.transitmovementspushnotifications.models.MovementId
import uk.gov.hmrc.transitmovementspushnotifications.models.MovementType
import uk.gov.hmrc.transitmovementspushnotifications.models.request.BoxAssociationRequest
import uk.gov.hmrc.transitmovementspushnotifications.repositories.BoxAssociationRepository
import uk.gov.hmrc.transitmovementspushnotifications.services.BoxAssociationFactory
import uk.gov.hmrc.transitmovementspushnotifications.services.PushPullNotificationService
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError.InsertNotAcknowledged
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.InvalidBoxId
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.UnexpectedError

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class PushNotificationControllerSpec extends SpecBase with ModelGenerators with TestActorSystem with ScalaCheckDrivenPropertyChecks {

  implicit val timeout: Timeout = 5.seconds

  val mockPushPullNotificationService      = mock[PushPullNotificationService]
  val mockMovementBoxAssociationRepository = mock[BoxAssociationRepository]
  val mockMovementBoxAssociationFactory    = mock[BoxAssociationFactory]

  def fakeRequest[A](
    movementId: MovementId,
    method: String,
    body: JsValue
  ): Request[JsValue] =
    FakeRequest(
      method = method,
      uri = routes.PushNotificationController.createBoxAssociation(movementId).url,
      headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)),
      body = body
    )

  override def afterEach() = {
    reset(mockPushPullNotificationService)
    reset(mockMovementBoxAssociationRepository)
    reset(mockMovementBoxAssociationFactory)
  }

  val controller =
    new PushNotificationController(
      stubControllerComponents(),
      mockPushPullNotificationService,
      mockMovementBoxAssociationRepository,
      mockMovementBoxAssociationFactory
    )

  "createBoxAssociation" - {

    val validBody: Gen[JsValue] =
      for {
        clientId     <- Gen.alphaNumStr
        movementType <- Gen.oneOf(MovementType.values)
      } yield Json.obj(
        "clientId"     -> clientId,
        "movementType" -> movementType.movementType
      )

    val invalidMovementTypeBody: Gen[JsValue] =
      for {
        clientId     <- Gen.alphaNumStr
        movementType <- Gen.hexStr
      } yield Json.obj(
        "clientId"     -> clientId,
        "movementType" -> movementType
      )

    val invalidBodyWithoutClientId: Gen[JsValue] =
      for {
        movementType <- Gen.oneOf(MovementType.values)
      } yield Json.obj(
        "movementType" -> movementType.movementType
      )

    "must return Created if successfully inserts box association" in forAll(
      arbitrary[BoxAssociation],
      validBody
    ) {
      (boxAssociation, body) =>
        when(mockPushPullNotificationService.getBoxId(any[BoxAssociationRequest])(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(EitherT.rightT(boxAssociation.boxId))

        when(mockMovementBoxAssociationFactory.create(any[String].asInstanceOf[BoxId], any[String].asInstanceOf[MovementId], any[MovementType]))
          .thenReturn(boxAssociation)

        when(mockMovementBoxAssociationRepository.insert(eqTo(boxAssociation)))
          .thenReturn(EitherT.rightT(Right(())))

        val request = fakeRequest(boxAssociation._id, POST, body)

        val result =
          controller.createBoxAssociation(boxAssociation._id)(request)

        status(result) mustBe CREATED
    }

    "must return BAD_REQUEST when invalid movementType provided in body" in forAll(
      arbitrary[BoxAssociation],
      invalidMovementTypeBody
    ) {
      (boxAssociation, body) =>
        when(mockPushPullNotificationService.getBoxId(any[BoxAssociationRequest])(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(EitherT.rightT(boxAssociation.boxId))

        when(mockMovementBoxAssociationFactory.create(any[String].asInstanceOf[BoxId], any[String].asInstanceOf[MovementId], any[MovementType]))
          .thenReturn(boxAssociation)

        when(mockMovementBoxAssociationRepository.insert(eqTo(boxAssociation)))
          .thenReturn(EitherT.rightT(Right(())))

        val request = fakeRequest(boxAssociation._id, POST, body)

        val result =
          controller.createBoxAssociation(boxAssociation._id)(request)

        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "BAD_REQUEST",
          "message" -> "Expected clientId and movementType to be present in the body"
        )
    }

    "must return BAD_REQUEST when clientId or movementType or both are missing in body" in forAll(
      arbitrary[BoxAssociation],
      invalidBodyWithoutClientId
    ) {
      (boxAssociation, body) =>
        when(mockPushPullNotificationService.getBoxId(any[BoxAssociationRequest])(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(EitherT.rightT(boxAssociation.boxId))

        when(mockMovementBoxAssociationFactory.create(any[String].asInstanceOf[BoxId], any[String].asInstanceOf[MovementId], any[MovementType]))
          .thenReturn(boxAssociation)

        when(mockMovementBoxAssociationRepository.insert(eqTo(boxAssociation)))
          .thenReturn(EitherT.rightT(Right(())))

        val request = fakeRequest(boxAssociation._id, POST, body)

        val result =
          controller.createBoxAssociation(boxAssociation._id)(request)

        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "BAD_REQUEST",
          "message" -> "Expected clientId and movementType to be present in the body"
        )
    }

    "must return BAD_REQUEST when boxId provided does not exist" in forAll(
      arbitrary[BoxAssociation],
      validBody
    ) {
      (boxAssociation, body) =>
        when(mockPushPullNotificationService.getBoxId(any[BoxAssociationRequest])(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(EitherT.leftT(InvalidBoxId))

        val request = fakeRequest(boxAssociation._id, POST, body)

        val result =
          controller.createBoxAssociation(boxAssociation._id)(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must return BAD_REQUEST when clientId, movementType and boxId are not present in the body" in forAll(
      arbitrary[BoxAssociation]
    ) {
      boxAssociation =>
        val request = fakeRequest(boxAssociation._id, POST, Json.obj())

        val result =
          controller.createBoxAssociation(boxAssociation._id)(request)

        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "BAD_REQUEST",
          "message" -> "Expected clientId and movementType to be present in the body"
        )
    }

    "must return INTERNAL_SERVER_ERROR when there's an unexpected PPNS failure" in forAll(
      arbitrary[BoxAssociation],
      validBody
    ) {
      (boxAssociation, body) =>
        when(mockPushPullNotificationService.getBoxId(any[BoxAssociationRequest])(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(EitherT.leftT(UnexpectedError(Some(new Exception("error")))))

        val request = fakeRequest(boxAssociation._id, POST, body)

        val result =
          controller.createBoxAssociation(boxAssociation._id)(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "INTERNAL_SERVER_ERROR",
          "message" -> "Internal server error"
        )
    }

    "must return INTERNAL_SERVER_ERROR if there's a mongo failure when inserting box association" in forAll(
      arbitrary[BoxAssociation],
      validBody
    ) {
      (boxAssociation, body) =>
        when(mockPushPullNotificationService.getBoxId(any[BoxAssociationRequest])(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(EitherT.rightT(boxAssociation.boxId))

        when(mockMovementBoxAssociationFactory.create(any[String].asInstanceOf[BoxId], any[String].asInstanceOf[MovementId], any[MovementType]))
          .thenReturn(boxAssociation)

        when(mockMovementBoxAssociationRepository.insert(any[BoxAssociation]))
          .thenReturn(EitherT.leftT(InsertNotAcknowledged(s"Insert failed for movement ${boxAssociation._id}")))

        val request = fakeRequest(boxAssociation._id, POST, body)

        val result =
          controller.createBoxAssociation(boxAssociation._id)(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "INTERNAL_SERVER_ERROR",
          "message" -> s"Insert failed for movement ${boxAssociation._id}"
        )
    }
  }

  "updateAssociationTTL" - {

    def fakeRequest(movementId: MovementId): FakeRequest[AnyContent] = FakeRequest(
      method = PATCH,
      uri = routes.PushNotificationController
        .updateAssociationTTL(movementId)
        .url,
      headers = FakeHeaders(),
      body = AnyContentAsEmpty
    )

    "return No Content when an update succeeded" in forAll(arbitrary[MovementId]) {
      movementId =>
        when(mockMovementBoxAssociationRepository.update(MovementId(eqTo(movementId.value)))).thenReturn(EitherT.rightT(()))

        val result =
          controller.updateAssociationTTL(movementId)(fakeRequest(movementId))

        status(result) mustBe NO_CONTENT
        contentAsString(result) mustBe ""
    }

    "return Internal Server Error when an update failed with an exception" in forAll(arbitrary[MovementId]) {
      movementId =>
        when(mockMovementBoxAssociationRepository.update(MovementId(eqTo(movementId.value)))).thenReturn(EitherT.leftT(MongoError.UnexpectedError()))

        val result =
          controller.updateAssociationTTL(movementId)(fakeRequest(movementId))

        status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "return Not Found when an update failed because the association does not exist" in forAll(arbitrary[MovementId]) {
      movementId =>
        when(mockMovementBoxAssociationRepository.update(MovementId(eqTo(movementId.value))))
          .thenReturn(EitherT.leftT(MongoError.DocumentNotFound(s"Could not find BoxAssociation with id: ${movementId.value}")))

        val result =
          controller.updateAssociationTTL(movementId)(fakeRequest(movementId))

        status(result) mustBe NOT_FOUND
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "NOT_FOUND",
          "message" -> s"Could not find BoxAssociation with id: ${movementId.value}"
        )
    }

  }

  "postNotification" - {

    val validBody: Source[ByteString, _] = Source.single(ByteString(<CC015>Hello</CC015>.mkString, StandardCharsets.UTF_8))

    def fakeRequest(boxAssociation: BoxAssociation, messageId: MessageId) = FakeRequest(
      method = POST,
      uri = routes.PushNotificationController
        .postNotification(boxAssociation._id, messageId)
        .url,
      headers = FakeHeaders(),
      body = validBody
    )

    "when called with a movement id, message id for which a box id is in the database" - {
      "should be successfully posted and return Unit ()" in forAll(arbitrary[BoxAssociation], arbitrary[MessageId]) {
        (boxAssociation, messageId) =>
          when(mockMovementBoxAssociationRepository.getBoxAssociation(any[String].asInstanceOf[MovementId]))
            .thenReturn(EitherT.rightT(boxAssociation))

          when(
            mockPushPullNotificationService
              .sendPushNotification(
                eqTo(boxAssociation),
                any[Option[String]],
                MessageId(eqTo(messageId.value)),
                any[Source[ByteString, _]]()
              )(
                any[ExecutionContext],
                any[HeaderCarrier],
                any[Materializer]
              )
          ).thenReturn(EitherT.rightT(()))

          val result =
            controller.postNotification(boxAssociation._id, messageId)(fakeRequest(boxAssociation, messageId))

          status(result) mustBe ACCEPTED
      }
    }

    "when called with a movement id for which there is no box id " - {
      "should return box not found error" in forAll(arbitrary[BoxAssociation], arbitrary[MessageId]) {
        (boxAssociation, messageId) =>
          when(mockMovementBoxAssociationRepository.getBoxAssociation(any[String].asInstanceOf[MovementId]))
            .thenReturn(EitherT.leftT(MongoError.DocumentNotFound("box id not found")))

          when(
            mockPushPullNotificationService
              .sendPushNotification(
                eqTo(boxAssociation),
                any[Option[String]],
                MessageId(eqTo(messageId.value)),
                any[Source[ByteString, _]]()
              )(
                any[ExecutionContext],
                any[HeaderCarrier],
                any[Materializer]
              )
          ).thenReturn(EitherT.rightT(()))

          val result =
            controller.postNotification(boxAssociation._id, messageId)(fakeRequest(boxAssociation, messageId))

          status(result) mustBe NOT_FOUND
      }
    }

    "when receiving an unexpected error" - {
      "should return an internal server error" in forAll(arbitrary[BoxAssociation], arbitrary[MessageId]) {
        (boxAssociation, messageId) =>
          when(mockMovementBoxAssociationRepository.getBoxAssociation(any[String].asInstanceOf[MovementId]))
            .thenReturn(EitherT.rightT(boxAssociation))

          when(
            mockPushPullNotificationService
              .sendPushNotification(
                eqTo(boxAssociation),
                any[Option[String]],
                MessageId(eqTo(messageId.value)),
                any[Source[ByteString, _]]()
              )(
                any[ExecutionContext],
                any[HeaderCarrier],
                any[Materializer]
              )
          ).thenReturn(EitherT.leftT(UnexpectedError(Some(new Exception(s"Unexpected error")))))

          val result =
            controller.postNotification(boxAssociation._id, messageId)(fakeRequest(boxAssociation, messageId))

          status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }

  }

}
