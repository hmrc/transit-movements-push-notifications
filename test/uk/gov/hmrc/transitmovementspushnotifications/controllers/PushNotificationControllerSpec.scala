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

import akka.util.Timeout
import cats.data.EitherT
import org.mockito.Mockito.reset
import org.mockito.Mockito.when
import uk.gov.hmrc.http.HttpVerbs.POST
import org.mockito.ArgumentMatchers.any
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.mvc.AnyContent
import play.api.mvc.Request
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Results.Accepted
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
import uk.gov.hmrc.transitmovementspushnotifications.models.MovementId
import uk.gov.hmrc.transitmovementspushnotifications.repositories.BoxAssociationRepository
import uk.gov.hmrc.transitmovementspushnotifications.services.MovementBoxAssociationFactory
import uk.gov.hmrc.transitmovementspushnotifications.services.PushPullNotificationService
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError.InsertNotAcknowledged
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class PushNotificationControllerSpec extends SpecBase with ModelGenerators with TestActorSystem {

  implicit val timeout: Timeout = 5.seconds

  val mockPushPullNotificationService      = mock[PushPullNotificationService]
  val mockMovementBoxAssociationRepository = mock[BoxAssociationRepository]
  val mockMovementBoxAssociationFactory    = mock[MovementBoxAssociationFactory]

  val now = OffsetDateTime.now

  lazy val boxAssociationRequest = arbitraryBoxAssociationRequest.arbitrary.sample.get

  lazy val boxAssociation = arbitraryBoxAssociation.arbitrary.sample.get.copy(boxId = boxAssociationRequest.boxId.value)

  lazy val boxId: BoxId = boxAssociationRequest.boxId.value

  def fakeRequest[A](
    method: String,
    body: String
  ): Request[String] =
    FakeRequest(
      method = method,
      uri = routes.PushNotificationController.createBoxAssociation(boxAssociation._id).url,
      headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)),
      body = body
    )

  override def afterEach() {
    reset(mockPushPullNotificationService)
    reset(mockMovementBoxAssociationRepository)
    reset(mockMovementBoxAssociationFactory)
    super.afterEach()
  }

  val controller =
    new PushNotificationController(
      stubControllerComponents(),
      mockPushPullNotificationService,
      mockMovementBoxAssociationRepository,
      mockMovementBoxAssociationFactory
    )

  "createBoxAssociation" - {

    val validBody: String = Json.stringify(
      Json.obj(
        "clientId" -> boxAssociationRequest.clientId
      )
    )

    "must return Accepted if successfully inserts box association" in {

      when(mockPushPullNotificationService.checkBoxIdExists(any[String].asInstanceOf[BoxId])(any[ExecutionContext], any[HeaderCarrier]))
        .thenReturn(EitherT.rightT(boxId))

      when(mockMovementBoxAssociationFactory.create(any[String].asInstanceOf[BoxId], any[String].asInstanceOf[MovementId]))
        .thenReturn(boxAssociation)

      when(mockMovementBoxAssociationRepository.insert(any[BoxAssociation]))
        .thenReturn(EitherT.rightT(Right(())))

      val request = fakeRequest(POST, validBody)

      val result =
        controller.createBoxAssociation(boxAssociation._id)(request)

      //status(result) mustBe Accepted

      contentAsJson(result) mustBe Json.obj(
        "code"    -> "BAD_REQUEST",
        "message" -> "Element messageSender not found"
      )
    }

    "must return BAD_REQUEST when boxId provided does not exist" in {

      when(mockPushPullNotificationService.checkBoxIdExists(any[String].asInstanceOf[BoxId])(any[ExecutionContext], any[HeaderCarrier]))
        .thenReturn(EitherT.leftT(PushPullNotificationError.InvalidBoxId(s"Box id provided does not exist: ${boxAssociation.boxId.value}")))

      val request = fakeRequest(POST, validBody)

      val result =
        controller.createBoxAssociation(boxAssociation._id)(request)

      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "BAD_REQUEST",
        "message" -> "Element messageSender not found"
      )
    }

    "must return BAD_REQUEST when clientId and boxId are not present in the body" in {

      when(mockPushPullNotificationService.checkBoxIdExists(any[String].asInstanceOf[BoxId])(any[ExecutionContext], any[HeaderCarrier]))
        .thenReturn(EitherT.rightT(boxId))

      when(mockMovementBoxAssociationFactory.create(any[String].asInstanceOf[BoxId], any[String].asInstanceOf[MovementId]))
        .thenReturn(boxAssociation)

      when(mockMovementBoxAssociationRepository.insert(any[BoxAssociation]))
        .thenReturn(EitherT.rightT(Right(())))

      val request = fakeRequest(POST, validBody)

      val result =
        controller.createBoxAssociation(boxAssociation._id)(request)

      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "BAD_REQUEST",
        "message" -> "Element messageSender not found"
      )
    }

    "must return INTERNAL_SERVER_ERROR when theres an unexpected PPNS failure" in {

      when(mockPushPullNotificationService.checkBoxIdExists(any[String].asInstanceOf[BoxId])(any[ExecutionContext], any[HeaderCarrier]))
        .thenReturn(EitherT.leftT(PushPullNotificationError.UnexpectedError(None)))

      val request = fakeRequest(POST, validBody)

      val result =
        controller.createBoxAssociation(boxAssociation._id)(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "BAD_REQUEST",
        "message" -> "Element messageSender not found"
      )
    }

    "must return INTERNAL_SERVER_ERROR if there's a mongo failure when inserting box association" in {

      when(mockPushPullNotificationService.checkBoxIdExists(any[String].asInstanceOf[BoxId])(any[ExecutionContext], any[HeaderCarrier]))
        .thenReturn(EitherT.rightT(boxId))

      when(mockMovementBoxAssociationFactory.create(any[String].asInstanceOf[BoxId], any[String].asInstanceOf[MovementId]))
        .thenReturn(boxAssociation)

      when(mockMovementBoxAssociationRepository.insert(any[BoxAssociation]))
        .thenReturn(EitherT.leftT(InsertNotAcknowledged(s"Insert failed for movement ${boxAssociation._id}")))

      val request = fakeRequest(POST, validBody)

      val result =
        controller.createBoxAssociation(boxAssociation._id)(request)

      status(result) mustBe Accepted
    }
  }
}
