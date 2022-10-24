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

import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.util.Timeout
import cats.data.EitherT
import org.mockito.Mockito.reset
import org.mockito.Mockito.when
import uk.gov.hmrc.http.HttpVerbs.POST
import org.mockito.ArgumentMatchers.any
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.ACCEPTED
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.CREATED
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.http.Status.NOT_FOUND
import play.api.mvc.Request
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsJson
import play.api.test.Helpers.status
import play.api.test.Helpers.stubControllerComponents
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.transitmovementspushnotifications.base.SpecBase
import uk.gov.hmrc.transitmovementspushnotifications.base.TestActorSystem
import uk.gov.hmrc.transitmovementspushnotifications.generators.ModelGenerators
import uk.gov.hmrc.transitmovementspushnotifications.models.BoxAssociation
import uk.gov.hmrc.transitmovementspushnotifications.models.BoxId
import uk.gov.hmrc.transitmovementspushnotifications.models.MessageId
import uk.gov.hmrc.transitmovementspushnotifications.models.MovementId
import uk.gov.hmrc.transitmovementspushnotifications.models.request.BoxAssociationRequest
import uk.gov.hmrc.transitmovementspushnotifications.repositories.BoxAssociationRepository
import uk.gov.hmrc.transitmovementspushnotifications.services.BoxAssociationFactory
import uk.gov.hmrc.transitmovementspushnotifications.services.PushPullNotificationService
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError.InsertNotAcknowledged
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError.BoxNotFound

import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.xml.NodeSeq

class PushNotificationControllerSpec extends SpecBase with ModelGenerators with TestActorSystem {

  implicit val timeout: Timeout = 5.seconds

  val mockPushPullNotificationService      = mock[PushPullNotificationService]
  val mockMovementBoxAssociationRepository = mock[BoxAssociationRepository]
  val mockMovementBoxAssociationFactory    = mock[BoxAssociationFactory]

  val lastUpdated: OffsetDateTime = OffsetDateTime.of(2022, 10, 17, 9, 1, 2, 0, ZoneOffset.UTC)
  val now                         = OffsetDateTime.now
  val clock: Clock                = Clock.fixed(now.toInstant, ZoneOffset.UTC)

  lazy val boxAssociationRequest = arbitraryBoxAssociationRequest.arbitrary.sample.get.copy(boxId = Some(BoxId("123")))

  lazy val boxAssociation = arbitraryBoxAssociation.arbitrary.sample.get.copy(boxId = boxAssociationRequest.boxId.value)

  lazy val boxId: BoxId = boxAssociationRequest.boxId.value

  def fakeRequest[A](
    method: String,
    body: JsValue
  ): Request[JsValue] =
    FakeRequest(
      method = method,
      uri = routes.PushNotificationController.createBoxAssociation(boxAssociation._id).url,
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
      mockMovementBoxAssociationFactory,
      clock
    )

  "createBoxAssociation" - {

    val validBody: JsValue = Json.obj(
      "clientId" -> boxAssociationRequest.clientId
    )

    "must return Created if successfully inserts box association" in {

      when(mockPushPullNotificationService.getBoxId(any[BoxAssociationRequest])(any[ExecutionContext], any[HeaderCarrier]))
        .thenReturn(EitherT.rightT(boxId))

      when(mockMovementBoxAssociationFactory.create(any[String].asInstanceOf[BoxId], any[String].asInstanceOf[MovementId]))
        .thenReturn(boxAssociation)

      when(mockMovementBoxAssociationRepository.insert(any[BoxAssociation]))
        .thenReturn(EitherT.rightT(Right(())))

      val request = fakeRequest(method = POST, body = validBody)

      val result =
        controller.createBoxAssociation(boxAssociation._id)(request)

      status(result) mustBe CREATED
    }

    "must return BAD_REQUEST when boxId provided does not exist" in {

      when(mockPushPullNotificationService.getBoxId(any[BoxAssociationRequest])(any[ExecutionContext], any[HeaderCarrier]))
        .thenReturn(EitherT.leftT(PushPullNotificationError.InvalidBoxId(s"Box id provided does not exist: ${boxAssociation.boxId.value}")))

      val request = fakeRequest(method = POST, body = validBody)

      val result =
        controller.createBoxAssociation(boxAssociation._id)(request)

      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "BAD_REQUEST",
        "message" -> "Box id provided does not exist: 123"
      )
    }

    "must return BAD_REQUEST when clientId and boxId are not present in the body" in {

      val request = fakeRequest(method = POST, body = Json.obj())

      val result =
        controller.createBoxAssociation(boxAssociation._id)(request)

      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "BAD_REQUEST",
        "message" -> "Expected clientId to be present in the body"
      )
    }

    "must return INTERNAL_SERVER_ERROR when there's an unexpected PPNS failure" in {

      when(mockPushPullNotificationService.getBoxId(any[BoxAssociationRequest])(any[ExecutionContext], any[HeaderCarrier]))
        .thenReturn(EitherT.leftT(PushPullNotificationError.UnexpectedError(Some(new Exception("error")))))

      val request = fakeRequest(method = POST, body = validBody)

      val result =
        controller.createBoxAssociation(boxAssociation._id)(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "INTERNAL_SERVER_ERROR",
        "message" -> "Internal server error"
      )
    }

    "must return INTERNAL_SERVER_ERROR if there's a mongo failure when inserting box association" in {

      when(mockPushPullNotificationService.getBoxId(any[BoxAssociationRequest])(any[ExecutionContext], any[HeaderCarrier]))
        .thenReturn(EitherT.rightT(boxId))

      when(mockMovementBoxAssociationFactory.create(any[String].asInstanceOf[BoxId], any[String].asInstanceOf[MovementId]))
        .thenReturn(boxAssociation)

      when(mockMovementBoxAssociationRepository.insert(any[BoxAssociation]))
        .thenReturn(EitherT.leftT(InsertNotAcknowledged(s"Insert failed for movement ${boxAssociation._id}")))

      val request = fakeRequest(method = POST, body = validBody)

      val result =
        controller.createBoxAssociation(boxAssociation._id)(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "INTERNAL_SERVER_ERROR",
        "message" -> s"Insert failed for movement ${boxAssociation._id}"
      )
    }
  }

  "postNotification" - {

    val contentLength                    = "50000"
    val validBody: Source[ByteString, _] = Source.single(ByteString(<CC015>Hello</CC015>.mkString, StandardCharsets.UTF_8))
    val emptyBody: Source[ByteString, _] = Source.single(ByteString(<CC015></CC015>.mkString, StandardCharsets.UTF_8))

    implicit val fakeRequest = FakeRequest(
      method = POST,
      uri = routes.PushNotificationController
        .postNotification(
          MovementId("movement-1"),
          MessageId("messageId-1")
        )
        .url,
      headers = FakeHeaders(),
      body = validBody
    )

    "must return Unit if message less than 80kB is successfully posted" in {

      when(mockMovementBoxAssociationRepository.getBoxId(any[String].asInstanceOf[MovementId], any[Clock]))
        .thenReturn(EitherT.rightT(Some(BoxId("box-id-1")))) // boxId

      when(
        mockPushPullNotificationService
          .sendPushNotification(any[String].asInstanceOf[Option[BoxId]], any[Option[String]], any[Source[ByteString, _]]())(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      ).thenReturn(EitherT.rightT(()))

      val result =
        controller.postNotification(MovementId("movement-1"), MessageId("messageId-1"))(fakeRequest)

      status(result) mustBe ACCEPTED
    }

    "must return box not found if the box cannot be located in the database" in {

      when(mockMovementBoxAssociationRepository.getBoxId(any[String].asInstanceOf[MovementId], any[Clock]))
        .thenReturn(EitherT.leftT(MongoError.DocumentNotFound("box id not found")))

      when(
        mockPushPullNotificationService
          .sendPushNotification(any[String].asInstanceOf[Option[BoxId]], any[Option[String]], any[Source[ByteString, _]]())(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      ).thenReturn(EitherT.rightT(()))

      val result =
        controller.postNotification(MovementId("movement-1"), MessageId("messageId-1"))(fakeRequest)

      status(result) mustBe NOT_FOUND
    }

  }
}
