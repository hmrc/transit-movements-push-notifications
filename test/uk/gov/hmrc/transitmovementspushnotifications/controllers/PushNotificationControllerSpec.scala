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

package uk.gov.hmrc.transitmovementspushnotifications.controllers

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.util.Timeout
import cats.data.EitherT
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.reset
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.Status._
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers.contentAsJson
import play.api.test.Helpers.contentAsString
import play.api.test.Helpers.status
import play.api.test.Helpers.stubControllerComponents
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpVerbs.PATCH
import uk.gov.hmrc.http.HttpVerbs.POST
import uk.gov.hmrc.transitmovementspushnotifications.base.SpecBase
import uk.gov.hmrc.transitmovementspushnotifications.base.TestActorSystem
import uk.gov.hmrc.transitmovementspushnotifications.controllers.errors.ConvertError
import uk.gov.hmrc.transitmovementspushnotifications.generators.ModelGenerators
import uk.gov.hmrc.transitmovementspushnotifications.models._
import uk.gov.hmrc.transitmovementspushnotifications.models.request.BoxAssociationRequest
import uk.gov.hmrc.transitmovementspushnotifications.repositories.BoxAssociationRepository
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError.InsertNotAcknowledged
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError
import uk.gov.hmrc.transitmovementspushnotifications.services.BoxAssociationFactory
import uk.gov.hmrc.transitmovementspushnotifications.services.PushPullNotificationService

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class PushNotificationControllerSpec extends SpecBase with ModelGenerators with TestActorSystem with ScalaCheckDrivenPropertyChecks with ConvertError {

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
  implicit val tfc: TemporaryFileCreator = SingletonTemporaryFileCreator

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
          .thenReturn(EitherT.rightT(boxAssociation))

        val request = fakeRequest(boxAssociation._id, POST, body)

        val expectedJson = Json.obj("boxId" -> boxAssociation.boxId.value)

        val result =
          controller.createBoxAssociation(boxAssociation._id)(request)

        status(result) mustBe CREATED
        contentAsJson(result) mustBe expectedJson
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
          .thenReturn(EitherT.rightT(boxAssociation))

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
          .thenReturn(EitherT.rightT(boxAssociation))

        val request = fakeRequest(boxAssociation._id, POST, body)

        val result =
          controller.createBoxAssociation(boxAssociation._id)(request)

        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "BAD_REQUEST",
          "message" -> "Expected clientId and movementType to be present in the body"
        )
    }

    "must return NOT_FOUND when boxId provided does not exist" in forAll(
      arbitrary[BoxAssociation],
      validBody
    ) {
      (boxAssociation, body) =>
        when(mockPushPullNotificationService.getBoxId(any[BoxAssociationRequest])(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(EitherT.leftT(PushPullNotificationError.BoxNotFound(BoxId("test"))))

        val request = fakeRequest(boxAssociation._id, POST, body)

        val result =
          controller.createBoxAssociation(boxAssociation._id)(request)

        status(result) mustBe NOT_FOUND
    }

    "must return NOT_FOUND when a default box does not exist" in forAll(
      arbitrary[BoxAssociation],
      validBody
    ) {
      (boxAssociation, body) =>
        when(mockPushPullNotificationService.getBoxId(any[BoxAssociationRequest])(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(EitherT.leftT(PushPullNotificationError.DefaultBoxNotFound))

        val request = fakeRequest(boxAssociation._id, POST, body)

        val result =
          controller.createBoxAssociation(boxAssociation._id)(request)

        status(result) mustBe NOT_FOUND
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
          .thenReturn(EitherT.leftT(PushPullNotificationError.UnexpectedError(Some(new Exception("error")))))

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

    val validXMLBody: Source[ByteString, _] = Source.single(ByteString(<CC015>Hello</CC015>.mkString, StandardCharsets.UTF_8))

    val validJSONBody = Json.toJson(
      Json.obj(
        "code" -> "SUCCESS",
        "message" ->
          s"The message for movement was successfully processed"
      )
    )

    def fakePostNotification[A](
      headers: FakeHeaders,
      body: A,
      boxAssociation: BoxAssociation,
      messageId: MessageId
    ): Request[A] =
      FakeRequest(
        method = POST,
        uri = routes.PushNotificationController
          .postNotification(boxAssociation._id, messageId)
          .url,
        headers = headers,
        body = body
      )

    "when called with a movement id, message id for which a box id is in the database with xml content type" - {

      "should return no content" in forAll(arbitrary[BoxAssociation], arbitrary[MessageId].suchThat(_.value.nonEmpty)) {
        (boxAssociation, messageId) =>
          when(mockMovementBoxAssociationRepository.getBoxAssociation(MovementId(eqTo(boxAssociation._id.value))))
            .thenReturn(EitherT.rightT(boxAssociation))

          when(
            mockPushPullNotificationService.sendPushNotification(
              eqTo(boxAssociation),
              any[Option[Long]],
              MessageId(eqTo(messageId.value)),
              any[Option[Source[ByteString, _]]],
              eqTo(NotificationType.MESSAGE_RECEIVED)
            )(any[ExecutionContext], any[HeaderCarrier], any[Materializer])
          ).thenReturn(EitherT.rightT(()))

          val result =
            controller.postNotification(boxAssociation._id, messageId)(
              fakePostNotification(FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)), validXMLBody, boxAssociation, messageId)
            )

          status(result) mustBe ACCEPTED
      }
    }

    "when called with a movement id, message id for which a box id is in the database with no content type" - {

      "should return no content" in forAll(
        arbitrary[BoxAssociation],
        arbitrary[MessageId].suchThat(_.value.nonEmpty)
      ) {
        (boxAssociation, messageId) =>
          when(mockMovementBoxAssociationRepository.getBoxAssociation(MovementId(eqTo(boxAssociation._id.value))))
            .thenReturn(EitherT.rightT(boxAssociation))

          when(
            mockPushPullNotificationService.sendPushNotification(
              eqTo(boxAssociation),
              any[Option[Long]],
              MessageId(eqTo(messageId.value)),
              any[Option[Source[ByteString, _]]],
              eqTo(NotificationType.MESSAGE_RECEIVED)
            )(any[ExecutionContext], any[HeaderCarrier], any[Materializer])
          ).thenReturn(EitherT.rightT(()))

          lazy val request = FakeRequest(
            method = "POST",
            uri = routes.PushNotificationController
              .postNotification(boxAssociation._id, messageId)
              .url,
            headers = FakeHeaders(),
            body = AnyContentAsEmpty
          )

          val result = controller.postNotification(boxAssociation._id, messageId)(request)

          status(result) mustBe ACCEPTED
      }
    }

    "when called with a movement id, message id for which a box id is in the database with json content type" - {

      "should return no content" in forAll(arbitrary[BoxAssociation], arbitrary[MessageId].suchThat(_.value.nonEmpty)) {
        (boxAssociation, messageId) =>
          when(mockMovementBoxAssociationRepository.getBoxAssociation(MovementId(eqTo(boxAssociation._id.value))))
            .thenReturn(EitherT.rightT(boxAssociation))

          when(
            mockPushPullNotificationService.sendPushNotification(
              eqTo(boxAssociation),
              any[Option[Long]],
              MessageId(eqTo(messageId.value)),
              any[Option[Source[ByteString, _]]],
              eqTo(NotificationType.SUBMISSION_NOTIFICATION)
            )(any[ExecutionContext], any[HeaderCarrier], any[Materializer])
          ).thenReturn(EitherT.rightT(()))

          val result =
            controller.postNotification(boxAssociation._id, messageId)(
              fakePostNotification(FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)), validJSONBody, boxAssociation, messageId)
            )

          status(result) mustBe ACCEPTED
      }
    }

    "when called with a movement id for which there is no box id " - {

      "should return box not found error" in forAll(arbitrary[BoxAssociation], arbitrary[MessageId]) {
        (boxAssociation, messageId) =>
          when(mockMovementBoxAssociationRepository.getBoxAssociation(MovementId(eqTo(boxAssociation._id.value))))
            .thenReturn(EitherT.leftT(MongoError.DocumentNotFound("box id not found")))

          when(
            mockPushPullNotificationService
              .sendPushNotification(
                eqTo(boxAssociation),
                any[Option[Long]],
                MessageId(eqTo(messageId.value)),
                any[Option[Source[ByteString, _]]],
                eqTo(NotificationType.MESSAGE_RECEIVED)
              )(
                any[ExecutionContext],
                any[HeaderCarrier],
                any[Materializer]
              )
          ).thenReturn(EitherT.rightT(()))

          val result =
            controller.postNotification(boxAssociation._id, messageId)(
              fakePostNotification(FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)), validXMLBody, boxAssociation, messageId)
            )

          status(result) mustBe NOT_FOUND
      }
    }

    "when receiving an unexpected error" - {

      "should return an internal server error" in forAll(arbitrary[BoxAssociation], arbitrary[MessageId]) {
        (boxAssociation, messageId) =>
          when(mockMovementBoxAssociationRepository.getBoxAssociation(MovementId(eqTo(boxAssociation._id.value))))
            .thenReturn(EitherT.rightT(boxAssociation))

          when(
            mockPushPullNotificationService
              .sendPushNotification(
                eqTo(boxAssociation),
                any[Option[Long]],
                MessageId(eqTo(messageId.value)),
                any[Option[Source[ByteString, _]]],
                eqTo(NotificationType.MESSAGE_RECEIVED)
              )(
                any[ExecutionContext],
                any[HeaderCarrier],
                any[Materializer]
              )
          ).thenReturn(EitherT.leftT(PushPullNotificationError.UnexpectedError(Some(new Exception(s"Unexpected error")))))

          val result =
            controller.postNotification(boxAssociation._id, messageId)(
              fakePostNotification(FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)), validXMLBody, boxAssociation, messageId)
            )

          status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }
  }
}
