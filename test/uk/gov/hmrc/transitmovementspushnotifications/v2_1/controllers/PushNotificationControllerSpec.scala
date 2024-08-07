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

package uk.gov.hmrc.transitmovementspushnotifications.v2_1.controllers

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.apache.pekko.util.Timeout
import cats.data.EitherT
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.Status._
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.functional.syntax.unlift
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.libs.json.__
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
import uk.gov.hmrc.internalauth.client.IAAction
import uk.gov.hmrc.internalauth.client.Predicate
import uk.gov.hmrc.internalauth.client.Resource
import uk.gov.hmrc.internalauth.client.ResourceLocation
import uk.gov.hmrc.internalauth.client.ResourceType
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.base.SpecBase
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.base.TestActorSystem
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.controllers.actions.InternalAuthActionProvider
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.controllers.errors.ConvertError
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.generators.ModelGenerators
import uk.gov.hmrc.transitmovementspushnotifications.models.common.BoxAssociation
import uk.gov.hmrc.transitmovementspushnotifications.models.common.BoxId
import uk.gov.hmrc.transitmovementspushnotifications.models.common.EORINumber
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MessageId
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MessageType
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MovementId
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MovementType
import uk.gov.hmrc.transitmovementspushnotifications.models.common.NotificationType
import uk.gov.hmrc.transitmovementspushnotifications.routing.routes
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.models.request.BoxAssociationRequest
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.repositories.BoxAssociationRepository
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.services.errors.MongoError.InsertNotAcknowledged
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.services.errors.MongoError
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.services.errors.PushPullNotificationError
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.services.BoxAssociationFactory
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.services.PushPullNotificationService

import java.nio.charset.StandardCharsets
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class PushNotificationControllerSpec extends SpecBase with ModelGenerators with TestActorSystem with ScalaCheckDrivenPropertyChecks with ConvertError {

  implicit val timeout: Timeout = 5.seconds

  val mockPushPullNotificationService: PushPullNotificationService   = mock[PushPullNotificationService]
  val mockMovementBoxAssociationRepository: BoxAssociationRepository = mock[BoxAssociationRepository]
  val mockMovementBoxAssociationFactory: BoxAssociationFactory       = mock[BoxAssociationFactory]
  val mockInternalAuthActionProvider: InternalAuthActionProvider     = mock[InternalAuthActionProvider]

  def fakeRequest(
    movementId: MovementId,
    method: String,
    body: JsValue
  ): Request[JsValue] =
    FakeRequest(
      method = method,
      uri = routes.VersionedRoutingController.createBoxAssociation(movementId).url,
      headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)),
      body = body
    )

  private def resetInternalAuth(): Unit = {
    reset(mockInternalAuthActionProvider)
    // any here, we'll use verify later to ensure the right permission was checked.
    when(mockInternalAuthActionProvider(any())(any())).thenReturn(DefaultActionBuilder(stubControllerComponents().parsers.defaultBodyParser))
  }

  override def afterEach(): Unit = {
    reset(mockPushPullNotificationService)
    reset(mockMovementBoxAssociationRepository)
    reset(mockMovementBoxAssociationFactory)
    resetInternalAuth()
  }
  implicit val tfc: TemporaryFileCreator = SingletonTemporaryFileCreator

  val controller =
    new PushNotificationController(
      stubControllerComponents(),
      mockPushPullNotificationService,
      mockMovementBoxAssociationRepository,
      mockMovementBoxAssociationFactory,
      mockInternalAuthActionProvider
    )

  private val notificationPermission = Predicate.Permission(
    Resource(
      ResourceType("transit-movements-push-notifications"),
      ResourceLocation("notification")
    ),
    IAAction("WRITE")
  )

  private val associatePermission = Predicate.Permission(
    Resource(
      ResourceType("transit-movements-push-notifications"),
      ResourceLocation("association")
    ),
    IAAction("WRITE")
  )

  "createBoxAssociation" - {

    case class JsonBody(clientId: String, movementType: String, enrollmentEORINumber: String)

    implicit val writes: OWrites[JsonBody] = (
      (__ \ "clientId").write[String] and
        (__ \ "movementType").write[String] and
        (__ \ "enrollmentEORINumber").write[String]
    )(unlift(JsonBody.unapply))

    val validBody: Gen[JsonBody] =
      for {
        clientId     <- Gen.alphaNumStr
        movementType <- Gen.oneOf(MovementType.values)
        eori         <- arbitrary[EORINumber]
      } yield JsonBody(clientId, movementType.movementType, eori.value)

    val invalidMovementTypeBody: Gen[JsonBody] =
      for {
        clientId     <- Gen.alphaNumStr
        movementType <- Gen.hexStr
        eori         <- arbitrary[EORINumber]
      } yield JsonBody(clientId, movementType, eori.value)

    val listOfFields = Gen.atLeastOne(
      "clientId",
      "movementType",
      "enrollmentEORINumber"
    )

    def findMovementType(t: String): Option[MovementType] = MovementType.values.find(
      movementType => movementType.movementType == t
    )

    "must return Created if successfully inserts box association" in forAll(
      arbitrary[BoxAssociation],
      validBody
    ) {
      (boxAssociation, body) =>
        resetInternalAuth()
        when(mockPushPullNotificationService.getBoxId(any[BoxAssociationRequest])(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(EitherT.rightT(boxAssociation.boxId))

        when(
          mockMovementBoxAssociationFactory.create(
            BoxId(eqTo(boxAssociation.boxId.value)),
            MovementId(eqTo(boxAssociation._id.value)),
            eqTo(findMovementType(body.movementType).get),
            EORINumber(eqTo(body.enrollmentEORINumber))
          )
        )
          .thenReturn(boxAssociation)

        when(mockMovementBoxAssociationRepository.insert(eqTo(boxAssociation)))
          .thenReturn(EitherT.rightT(boxAssociation))

        val request = fakeRequest(boxAssociation._id, POST, Json.toJson(body))

        val expectedJson = Json.obj("boxId" -> boxAssociation.boxId.value)

        val result =
          controller.createBoxAssociation(boxAssociation._id)(request)

        status(result) mustBe CREATED
        contentAsJson(result) mustBe expectedJson

        verify(mockInternalAuthActionProvider, times(1)).apply(eqTo(associatePermission))(any())
        verify(mockInternalAuthActionProvider, times(0)).apply(eqTo(notificationPermission))(any())
    }

    "must return BAD_REQUEST when invalid movementType provided in body" in forAll(
      arbitrary[BoxAssociation],
      invalidMovementTypeBody
    ) {
      (boxAssociation, body) =>
        resetInternalAuth()
        when(mockPushPullNotificationService.getBoxId(any[BoxAssociationRequest])(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(EitherT.rightT(boxAssociation.boxId))

        val request = fakeRequest(boxAssociation._id, POST, Json.toJson(body))

        val result =
          controller.createBoxAssociation(boxAssociation._id)(request)

        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "BAD_REQUEST",
          "message" -> "Expected clientId, movementType and enrollmentEORINumber to be present in the body"
        )

        verify(mockInternalAuthActionProvider, times(1)).apply(eqTo(associatePermission))(any())
        verify(mockInternalAuthActionProvider, times(0)).apply(eqTo(notificationPermission))(any())
    }

    "must return BAD_REQUEST when clientId, movementType, enrollmentEORINumber or any combination thereof are missing in body" in forAll(
      arbitrary[BoxAssociation],
      validBody,
      listOfFields
    ) {
      (boxAssociation, validBody, fieldsToExclude) =>
        @tailrec
        def excludeFields(jsonBody: JsObject, fields: Iterable[String]): JsObject =
          (fields: @unchecked) match {
            case head :: tail => excludeFields(jsonBody - head, tail)
            case Nil          => jsonBody
          }

        val body = excludeFields(Json.toJsObject(validBody), fieldsToExclude)

        resetInternalAuth()
        when(mockPushPullNotificationService.getBoxId(any[BoxAssociationRequest])(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(EitherT.rightT(boxAssociation.boxId))

        val request = fakeRequest(boxAssociation._id, POST, body)

        val result =
          controller.createBoxAssociation(boxAssociation._id)(request)

        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "BAD_REQUEST",
          "message" -> "Expected clientId, movementType and enrollmentEORINumber to be present in the body"
        )

        verify(mockInternalAuthActionProvider, times(1)).apply(eqTo(associatePermission))(any())
        verify(mockInternalAuthActionProvider, times(0)).apply(eqTo(notificationPermission))(any())
    }

    "must return NOT_FOUND when boxId provided does not exist" in forAll(
      arbitrary[BoxAssociation],
      validBody
    ) {
      (boxAssociation, body) =>
        resetInternalAuth()
        when(mockPushPullNotificationService.getBoxId(any[BoxAssociationRequest])(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(EitherT.leftT(PushPullNotificationError.BoxNotFound(BoxId("test"))))

        val request = fakeRequest(boxAssociation._id, POST, Json.toJson(body))

        val result =
          controller.createBoxAssociation(boxAssociation._id)(request)

        status(result) mustBe NOT_FOUND

        verify(mockInternalAuthActionProvider, times(1)).apply(eqTo(associatePermission))(any())
        verify(mockInternalAuthActionProvider, times(0)).apply(eqTo(notificationPermission))(any())
    }

    "must return NOT_FOUND when a default box does not exist" in forAll(
      arbitrary[BoxAssociation],
      validBody
    ) {
      (boxAssociation, body) =>
        resetInternalAuth()
        when(mockPushPullNotificationService.getBoxId(any[BoxAssociationRequest])(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(EitherT.leftT(PushPullNotificationError.DefaultBoxNotFound))

        val request = fakeRequest(boxAssociation._id, POST, Json.toJson(body))

        val result =
          controller.createBoxAssociation(boxAssociation._id)(request)

        status(result) mustBe NOT_FOUND

        verify(mockInternalAuthActionProvider, times(1)).apply(eqTo(associatePermission))(any())
        verify(mockInternalAuthActionProvider, times(0)).apply(eqTo(notificationPermission))(any())
    }

    "must return BAD_REQUEST when clientId, movementType and boxId are not present in the body" in forAll(
      arbitrary[BoxAssociation]
    ) {
      boxAssociation =>
        resetInternalAuth()
        val request = fakeRequest(boxAssociation._id, POST, Json.obj())

        val result =
          controller.createBoxAssociation(boxAssociation._id)(request)

        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "BAD_REQUEST",
          "message" -> "Expected clientId, movementType and enrollmentEORINumber to be present in the body"
        )

        verify(mockInternalAuthActionProvider, times(1)).apply(eqTo(associatePermission))(any())
        verify(mockInternalAuthActionProvider, times(0)).apply(eqTo(notificationPermission))(any())
    }

    "must return INTERNAL_SERVER_ERROR when there's an unexpected PPNS failure" in forAll(
      arbitrary[BoxAssociation],
      validBody
    ) {
      (boxAssociation, body) =>
        resetInternalAuth()
        when(mockPushPullNotificationService.getBoxId(any[BoxAssociationRequest])(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(EitherT.leftT(PushPullNotificationError.UnexpectedError(Some(new Exception("error")))))

        val request = fakeRequest(boxAssociation._id, POST, Json.toJson(body))

        val result =
          controller.createBoxAssociation(boxAssociation._id)(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "INTERNAL_SERVER_ERROR",
          "message" -> "Internal server error"
        )

        verify(mockInternalAuthActionProvider, times(1)).apply(eqTo(associatePermission))(any())
        verify(mockInternalAuthActionProvider, times(0)).apply(eqTo(notificationPermission))(any())
    }

    "must return INTERNAL_SERVER_ERROR if there's a mongo failure when inserting box association" in forAll(
      arbitrary[BoxAssociation],
      validBody
    ) {
      (boxAssociation, body) =>
        resetInternalAuth()
        when(mockPushPullNotificationService.getBoxId(any[BoxAssociationRequest])(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(EitherT.rightT(boxAssociation.boxId))

        when(
          mockMovementBoxAssociationFactory.create(
            BoxId(eqTo(boxAssociation.boxId.value)),
            MovementId(eqTo(boxAssociation._id.value)),
            eqTo(findMovementType(body.movementType).get),
            EORINumber(eqTo(body.enrollmentEORINumber))
          )
        )
          .thenReturn(boxAssociation)

        when(mockMovementBoxAssociationRepository.insert(eqTo(boxAssociation)))
          .thenReturn(EitherT.leftT(InsertNotAcknowledged(s"Insert failed for movement ${boxAssociation._id}")))

        val request = fakeRequest(boxAssociation._id, POST, Json.toJson(body))

        val result =
          controller.createBoxAssociation(boxAssociation._id)(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "INTERNAL_SERVER_ERROR",
          "message" -> s"Insert failed for movement ${boxAssociation._id}"
        )

        verify(mockInternalAuthActionProvider, times(1)).apply(eqTo(associatePermission))(any())
        verify(mockInternalAuthActionProvider, times(0)).apply(eqTo(notificationPermission))(any())
    }
  }

  "updateAssociationTTL" - {

    def fakeRequest(movementId: MovementId): FakeRequest[AnyContent] = FakeRequest(
      method = PATCH,
      uri = routes.VersionedRoutingController
        .updateAssociationTTL(movementId)
        .url,
      headers = FakeHeaders(),
      body = AnyContentAsEmpty
    )

    "return No Content when an update succeeded" in forAll(arbitrary[MovementId]) {
      movementId =>
        resetInternalAuth()
        when(mockMovementBoxAssociationRepository.update(MovementId(eqTo(movementId.value)))).thenReturn(EitherT.rightT(()))

        val result =
          controller.updateAssociationTTL(movementId)(fakeRequest(movementId))

        status(result) mustBe NO_CONTENT
        contentAsString(result) mustBe ""

        verify(mockInternalAuthActionProvider, times(1)).apply(eqTo(associatePermission))(any())
        verify(mockInternalAuthActionProvider, times(0)).apply(eqTo(notificationPermission))(any())
    }

    "return Internal Server Error when an update failed with an exception" in forAll(arbitrary[MovementId]) {
      movementId =>
        resetInternalAuth()
        when(mockMovementBoxAssociationRepository.update(MovementId(eqTo(movementId.value)))).thenReturn(EitherT.leftT(MongoError.UnexpectedError()))

        val result =
          controller.updateAssociationTTL(movementId)(fakeRequest(movementId))

        status(result) mustBe INTERNAL_SERVER_ERROR

        verify(mockInternalAuthActionProvider, times(1)).apply(eqTo(associatePermission))(any())
        verify(mockInternalAuthActionProvider, times(0)).apply(eqTo(notificationPermission))(any())
    }

    "return Not Found when an update failed because the association does not exist" in forAll(arbitrary[MovementId]) {
      movementId =>
        resetInternalAuth()
        when(mockMovementBoxAssociationRepository.update(MovementId(eqTo(movementId.value))))
          .thenReturn(EitherT.leftT(MongoError.DocumentNotFound(s"Could not find BoxAssociation with id: ${movementId.value}")))

        val result =
          controller.updateAssociationTTL(movementId)(fakeRequest(movementId))

        status(result) mustBe NOT_FOUND
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "NOT_FOUND",
          "message" -> s"Could not find BoxAssociation with id: ${movementId.value}"
        )

        verify(mockInternalAuthActionProvider, times(1)).apply(eqTo(associatePermission))(any())
        verify(mockInternalAuthActionProvider, times(0)).apply(eqTo(notificationPermission))(any())
    }

  }

  "postNotificationByContentType" - {

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
        uri = routes.VersionedRoutingController
          .postNotificationByContentType(boxAssociation._id, messageId)
          .url,
        headers = headers,
        body = body
      )

    "when called with a movement id, message id for which a box id is in the database with xml content type" - {

      "should return no content" in forAll(arbitrary[BoxAssociation], arbitrary[MessageId].suchThat(_.value.nonEmpty), Gen.option(arbitrary[MessageType])) {
        (boxAssociation, messageId, messageTypeMaybe) =>
          resetInternalAuth()
          when(mockMovementBoxAssociationRepository.getBoxAssociation(MovementId(eqTo(boxAssociation._id.value))))
            .thenReturn(EitherT.rightT(boxAssociation))

          when(
            mockPushPullNotificationService.sendPushNotification(
              eqTo(boxAssociation),
              any[Long],
              MessageId(eqTo(messageId.value)),
              any[Source[ByteString, _]],
              eqTo(NotificationType.MESSAGE_RECEIVED),
              eqTo(messageTypeMaybe)
            )(any[ExecutionContext], any[HeaderCarrier], any[Materializer])
          ).thenReturn(EitherT.rightT(()))

          val result =
            controller.postNotificationByContentType(boxAssociation._id, messageId)(
              fakePostNotification(
                FakeHeaders(
                  Seq(
                    HeaderNames.CONTENT_TYPE -> MimeTypes.XML
                  ) ++ messageTypeMaybe
                    .map(
                      x => Seq("X-Message-Type" -> x.value)
                    )
                    .getOrElse(Seq())
                ),
                validXMLBody,
                boxAssociation,
                messageId
              )
            )

          status(result) mustBe ACCEPTED

          verify(mockInternalAuthActionProvider, times(0)).apply(eqTo(associatePermission))(any())
          verify(mockInternalAuthActionProvider, times(1)).apply(eqTo(notificationPermission))(any())
      }
    }

    "when called with a movement id, message id for which a box id is in the database with no content type" - {

      "should return no content" in forAll(
        arbitrary[BoxAssociation],
        arbitrary[MessageId].suchThat(_.value.nonEmpty),
        Gen.option(arbitrary[MessageType])
      ) {
        (boxAssociation, messageId, messageTypeMaybe) =>
          resetInternalAuth()
          when(mockMovementBoxAssociationRepository.getBoxAssociation(MovementId(eqTo(boxAssociation._id.value))))
            .thenReturn(EitherT.rightT(boxAssociation))

          when(
            mockPushPullNotificationService.sendPushNotification(
              eqTo(boxAssociation),
              eqTo(0L),
              MessageId(eqTo(messageId.value)),
              any[Source[ByteString, _]],
              eqTo(NotificationType.MESSAGE_RECEIVED),
              eqTo(messageTypeMaybe)
            )(any[ExecutionContext], any[HeaderCarrier], any[Materializer])
          ).thenReturn(EitherT.rightT(()))

          lazy val request = FakeRequest(
            method = "POST",
            uri = routes.VersionedRoutingController
              .postNotificationByContentType(boxAssociation._id, messageId)
              .url,
            headers = FakeHeaders(
              messageTypeMaybe
                .map(
                  x => Seq("X-Message-Type" -> x.value)
                )
                .getOrElse(Seq())
            ),
            body = AnyContentAsEmpty
          )

          val result = controller.postNotificationByContentType(boxAssociation._id, messageId)(request)

          status(result) mustBe ACCEPTED
          verify(mockInternalAuthActionProvider, times(0)).apply(eqTo(associatePermission))(any())
          verify(mockInternalAuthActionProvider, times(1)).apply(eqTo(notificationPermission))(any())
      }
    }

    "when called with a movement id, message id for which a box id is in the database with json content type" - {

      "should return no content" in forAll(arbitrary[BoxAssociation], arbitrary[MessageId].suchThat(_.value.nonEmpty), Gen.option(arbitrary[MessageType])) {
        (boxAssociation, messageId, messageTypeMaybe) =>
          resetInternalAuth()
          when(mockMovementBoxAssociationRepository.getBoxAssociation(MovementId(eqTo(boxAssociation._id.value))))
            .thenReturn(EitherT.rightT(boxAssociation))

          when(
            mockPushPullNotificationService.sendPushNotification(
              eqTo(boxAssociation),
              any[Long],
              MessageId(eqTo(messageId.value)),
              any[Source[ByteString, _]],
              eqTo(NotificationType.SUBMISSION_NOTIFICATION),
              eqTo(messageTypeMaybe)
            )(any[ExecutionContext], any[HeaderCarrier], any[Materializer])
          ).thenReturn(EitherT.rightT(()))

          val result =
            controller.postNotificationByContentType(boxAssociation._id, messageId)(
              fakePostNotification(
                FakeHeaders(
                  Seq(
                    HeaderNames.CONTENT_TYPE -> MimeTypes.JSON
                  ) ++ messageTypeMaybe
                    .map(
                      x => Seq("X-Message-Type" -> x.value)
                    )
                    .getOrElse(Seq())
                ),
                validJSONBody,
                boxAssociation,
                messageId
              )
            )

          status(result) mustBe ACCEPTED

          verify(mockInternalAuthActionProvider, times(0)).apply(eqTo(associatePermission))(any())
          verify(mockInternalAuthActionProvider, times(1)).apply(eqTo(notificationPermission))(any())
      }
    }

    "when called with a movement id for which there is no box id " - {

      "should return box not found error" in forAll(arbitrary[BoxAssociation], arbitrary[MessageId], Gen.option(arbitrary[MessageType])) {
        (boxAssociation, messageId, messageTypeMaybe) =>
          resetInternalAuth()
          when(mockMovementBoxAssociationRepository.getBoxAssociation(MovementId(eqTo(boxAssociation._id.value))))
            .thenReturn(EitherT.leftT(MongoError.DocumentNotFound("box id not found")))

          when(
            mockPushPullNotificationService
              .sendPushNotification(
                eqTo(boxAssociation),
                any[Long],
                MessageId(eqTo(messageId.value)),
                any[Source[ByteString, _]],
                eqTo(NotificationType.MESSAGE_RECEIVED),
                eqTo(messageTypeMaybe)
              )(
                any[ExecutionContext],
                any[HeaderCarrier],
                any[Materializer]
              )
          ).thenReturn(EitherT.rightT(()))

          val result =
            controller.postNotificationByContentType(boxAssociation._id, messageId)(
              fakePostNotification(
                FakeHeaders(
                  Seq(
                    HeaderNames.CONTENT_TYPE -> MimeTypes.XML
                  ) ++ messageTypeMaybe
                    .map(
                      x => Seq("X-Message-Type" -> x.value)
                    )
                    .getOrElse(Seq())
                ),
                validXMLBody,
                boxAssociation,
                messageId
              )
            )

          status(result) mustBe NOT_FOUND

          verify(mockInternalAuthActionProvider, times(0)).apply(eqTo(associatePermission))(any())
          verify(mockInternalAuthActionProvider, times(1)).apply(eqTo(notificationPermission))(any())
      }
    }

    "when receiving an unexpected error" - {

      "should return an internal server error" in forAll(arbitrary[BoxAssociation], arbitrary[MessageId], Gen.option(arbitrary[MessageType])) {
        (boxAssociation, messageId, messageTypeMaybe) =>
          resetInternalAuth()
          when(mockMovementBoxAssociationRepository.getBoxAssociation(MovementId(eqTo(boxAssociation._id.value))))
            .thenReturn(EitherT.rightT(boxAssociation))

          when(
            mockPushPullNotificationService
              .sendPushNotification(
                eqTo(boxAssociation),
                any[Long],
                MessageId(eqTo(messageId.value)),
                any[Source[ByteString, _]],
                eqTo(NotificationType.MESSAGE_RECEIVED),
                eqTo(messageTypeMaybe)
              )(
                any[ExecutionContext],
                any[HeaderCarrier],
                any[Materializer]
              )
          ).thenReturn(EitherT.leftT(PushPullNotificationError.UnexpectedError(Some(new Exception(s"Unexpected error")))))

          val result =
            controller.postNotificationByContentType(boxAssociation._id, messageId)(
              fakePostNotification(
                FakeHeaders(
                  Seq(
                    HeaderNames.CONTENT_TYPE -> MimeTypes.XML
                  ) ++ messageTypeMaybe
                    .map(
                      x => Seq("X-Message-Type" -> x.value)
                    )
                    .getOrElse(Seq())
                ),
                validXMLBody,
                boxAssociation,
                messageId
              )
            )

          status(result) mustBe INTERNAL_SERVER_ERROR

          verify(mockInternalAuthActionProvider, times(0)).apply(eqTo(associatePermission))(any())
          verify(mockInternalAuthActionProvider, times(1)).apply(eqTo(notificationPermission))(any())
      }
    }
  }

  "postNotification" - {

    val validXMLBody: Source[ByteString, _] = Source.single(ByteString(<CC015>Hello</CC015>.mkString, StandardCharsets.UTF_8))

    def fakePostNotification[A](
      headers: FakeHeaders,
      body: A,
      boxAssociation: BoxAssociation,
      messageId: MessageId,
      notificationType: NotificationType
    ): Request[A] =
      FakeRequest(
        method = POST,
        uri = routes.VersionedRoutingController
          .postNotification(boxAssociation._id, messageId, notificationType)
          .url,
        headers = headers,
        body = body
      )

    "when called with a movement id, message id for which a box id is in the database with a given notification type" - {

      "should return no content" in forAll(
        arbitrary[BoxAssociation],
        arbitrary[MessageId].suchThat(_.value.nonEmpty),
        Gen.option(arbitrary[MessageType]),
        arbitrary[NotificationType]
      ) {
        (boxAssociation, messageId, messageTypeMaybe, notificationType) =>
          resetInternalAuth()
          when(mockMovementBoxAssociationRepository.getBoxAssociation(MovementId(eqTo(boxAssociation._id.value))))
            .thenReturn(EitherT.rightT(boxAssociation))

          when(
            mockPushPullNotificationService.sendPushNotification(
              eqTo(boxAssociation),
              any[Long],
              MessageId(eqTo(messageId.value)),
              any[Source[ByteString, _]],
              eqTo(notificationType),
              eqTo(messageTypeMaybe)
            )(any[ExecutionContext], any[HeaderCarrier], any[Materializer])
          ).thenReturn(EitherT.rightT(()))

          val result =
            controller.postNotification(boxAssociation._id, messageId, notificationType)(
              fakePostNotification(
                FakeHeaders(
                  Seq(
                    HeaderNames.CONTENT_TYPE -> MimeTypes.XML
                  ) ++ messageTypeMaybe
                    .map(
                      x => Seq("X-Message-Type" -> x.value)
                    )
                    .getOrElse(Seq())
                ),
                validXMLBody,
                boxAssociation,
                messageId,
                notificationType
              )
            )

          status(result) mustBe ACCEPTED

          verify(mockInternalAuthActionProvider, times(0)).apply(eqTo(associatePermission))(any())
          verify(mockInternalAuthActionProvider, times(1)).apply(eqTo(notificationPermission))(any())
      }
    }

    "when called with a movement id for which there is no box id " - {

      "should return not found" in forAll(
        arbitrary[BoxAssociation],
        arbitrary[MessageId].suchThat(_.value.nonEmpty),
        Gen.option(arbitrary[MessageType]),
        arbitrary[NotificationType]
      ) {
        (boxAssociation, messageId, messageTypeMaybe, notificationType) =>
          resetInternalAuth()
          when(mockMovementBoxAssociationRepository.getBoxAssociation(MovementId(eqTo(boxAssociation._id.value))))
            .thenReturn(EitherT.leftT(MongoError.DocumentNotFound("box id not found")))

          when(
            mockPushPullNotificationService
              .sendPushNotification(
                eqTo(boxAssociation),
                any[Long],
                MessageId(eqTo(messageId.value)),
                any[Source[ByteString, _]],
                eqTo(notificationType),
                eqTo(messageTypeMaybe)
              )(
                any[ExecutionContext],
                any[HeaderCarrier],
                any[Materializer]
              )
          ).thenReturn(EitherT.rightT(()))

          val result =
            controller.postNotification(boxAssociation._id, messageId, notificationType)(
              fakePostNotification(
                FakeHeaders(
                  Seq(
                    HeaderNames.CONTENT_TYPE -> MimeTypes.XML
                  ) ++ messageTypeMaybe
                    .map(
                      x => Seq("X-Message-Type" -> x.value)
                    )
                    .getOrElse(Seq())
                ),
                validXMLBody,
                boxAssociation,
                messageId,
                notificationType
              )
            )

          status(result) mustBe NOT_FOUND

          verify(mockInternalAuthActionProvider, times(0)).apply(eqTo(associatePermission))(any())
          verify(mockInternalAuthActionProvider, times(1)).apply(eqTo(notificationPermission))(any())
      }
    }

    "when receiving an unexpected error" - {

      "should return an internal server error" in forAll(
        arbitrary[BoxAssociation],
        arbitrary[MessageId].suchThat(_.value.nonEmpty),
        Gen.option(arbitrary[MessageType]),
        arbitrary[NotificationType]
      ) {
        (boxAssociation, messageId, messageTypeMaybe, notificationType) =>
          resetInternalAuth()
          when(mockMovementBoxAssociationRepository.getBoxAssociation(MovementId(eqTo(boxAssociation._id.value))))
            .thenReturn(EitherT.rightT(boxAssociation))

          when(
            mockPushPullNotificationService
              .sendPushNotification(
                eqTo(boxAssociation),
                any[Long],
                MessageId(eqTo(messageId.value)),
                any[Source[ByteString, _]],
                eqTo(notificationType),
                eqTo(messageTypeMaybe)
              )(
                any[ExecutionContext],
                any[HeaderCarrier],
                any[Materializer]
              )
          ).thenReturn(EitherT.leftT(PushPullNotificationError.UnexpectedError(Some(new Exception(s"Unexpected error")))))

          val result =
            controller.postNotification(boxAssociation._id, messageId, notificationType)(
              fakePostNotification(
                FakeHeaders(
                  Seq(
                    HeaderNames.CONTENT_TYPE -> MimeTypes.XML
                  ) ++ messageTypeMaybe
                    .map(
                      x => Seq("X-Message-Type" -> x.value)
                    )
                    .getOrElse(Seq())
                ),
                validXMLBody,
                boxAssociation,
                messageId,
                notificationType
              )
            )

          status(result) mustBe INTERNAL_SERVER_ERROR

          verify(mockInternalAuthActionProvider, times(0)).apply(eqTo(associatePermission))(any())
          verify(mockInternalAuthActionProvider, times(1)).apply(eqTo(notificationPermission))(any())
      }
    }
  }
}
