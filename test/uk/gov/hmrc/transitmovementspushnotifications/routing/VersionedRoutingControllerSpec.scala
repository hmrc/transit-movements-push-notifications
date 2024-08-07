/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.transitmovementspushnotifications.routing

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.DefaultHttpErrorHandler
import play.api.http.HeaderNames
import play.api.http.HttpErrorConfig
import play.api.http.MimeTypes
import play.api.http.Status.ACCEPTED
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.CREATED
import play.api.http.Status.NO_CONTENT
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.functional.syntax.unlift
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.libs.json.__
import play.api.mvc.Action
import play.api.mvc._
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import play.api.test.Helpers.defaultAwaitTimeout
import play.api.test.Helpers.status
import play.api.test.Helpers.stubControllerComponents
import uk.gov.hmrc.transitmovementspushnotifications.base.TestActorSystem
import uk.gov.hmrc.transitmovementspushnotifications.config.AppConfig
import uk.gov.hmrc.transitmovementspushnotifications.config.Constants
import uk.gov.hmrc.transitmovementspushnotifications.controllers.{PushNotificationController => TransitionalPushNotificationController}
import uk.gov.hmrc.transitmovementspushnotifications.controllers.actions.{InternalAuthActionProvider => TransitionalInternalAuthActionProvider}
import uk.gov.hmrc.transitmovementspushnotifications.models.common.EORINumber
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MessageId
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MovementId
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MovementType
import uk.gov.hmrc.transitmovementspushnotifications.models.common.NotificationType
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.controllers.actions.{InternalAuthActionProvider => FinalInternalAuthActionProvider}
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.controllers.{PushNotificationController => FinalPushNotificationController}
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.generators.ModelGenerators
import uk.gov.hmrc.transitmovementspushnotifications.repositories.{BoxAssociationRepository => TransitionalBoxAssociationRepository}
import uk.gov.hmrc.transitmovementspushnotifications.services.{BoxAssociationFactory => TransitionalBoxAssociationFactory}
import uk.gov.hmrc.transitmovementspushnotifications.services.{PushPullNotificationService => TransitionalPushPullNotificationService}
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.repositories.{BoxAssociationRepository => FinalBoxAssociationRepository}
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.services.{BoxAssociationFactory => FinalBoxAssociationFactory}
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.services.{PushPullNotificationService => FinalPushPullNotificationService}

import scala.concurrent.ExecutionContext.Implicits.global
import java.time.Clock
import scala.concurrent.Future

class VersionedRoutingControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures with ModelGenerators {

  "createBoxAssociation" should {
    "call the transitional controller when APIVersion non 'final' value has been sent" in new Setup {
      val route                = routes.VersionedRoutingController.createBoxAssociation(boxAssociation._id)
      val headers: FakeHeaders = FakeHeaders(Seq(Constants.APIVersionHeaderKey -> "anything", HeaderNames.CONTENT_TYPE -> MimeTypes.JSON))
      val request              = FakeRequest(route.method, route.url, headers, Json.toJson(validBody.sample.get))

      val result = controller.createBoxAssociation(boxAssociation._id)(request)

      status(result) shouldBe CREATED
      contentAsString(result) shouldBe "transitional"
    }

    "call the transitional controller when no APIVersion header has been sent" in new Setup {
      val route   = routes.VersionedRoutingController.createBoxAssociation(boxAssociation._id)
      val request = FakeRequest(route.method, route.url, FakeHeaders(Seq.empty), Json.toJson(validBody.sample.get))

      val result = controller.createBoxAssociation(boxAssociation._id)(request)

      status(result) shouldBe CREATED
      contentAsString(result) shouldBe "transitional"
    }

    "call the versioned controller when APIVersion header 'final' has been sent" in new Setup {
      val route   = routes.VersionedRoutingController.createBoxAssociation(boxAssociation._id)
      val request = FakeRequest(route.method, route.url, FakeHeaders(Seq(Constants.APIVersionHeaderKey -> "final")), Json.toJson(validBody.sample.get))

      val result = controller.createBoxAssociation(boxAssociation._id)(request)

      status(result) shouldBe CREATED
      contentAsString(result) shouldBe "final"
    }

  }

  "updateAssociationTTL" should {
    "call the transitional controller when APIVersion non 'final' value has been sent" in new Setup {
      val route                = routes.VersionedRoutingController.updateAssociationTTL(boxAssociation._id)
      val headers: FakeHeaders = FakeHeaders(Seq(Constants.APIVersionHeaderKey -> "anything", HeaderNames.CONTENT_TYPE -> MimeTypes.JSON))
      val request              = FakeRequest(route.method, route.url, headers, Json.toJson(validBody.sample.get))

      val result = controller.updateAssociationTTL(boxAssociation._id)(request)

      status(result) shouldBe NO_CONTENT
    }

    "call the transitional controller when no APIVersion header has been sent" in new Setup {
      val route   = routes.VersionedRoutingController.updateAssociationTTL(boxAssociation._id)
      val request = FakeRequest(route.method, route.url, FakeHeaders(Seq.empty), Json.toJson(validBody.sample.get))

      val result = controller.updateAssociationTTL(boxAssociation._id)(request)

      status(result) shouldBe NO_CONTENT
    }

    "call the versioned controller when APIVersion header 'final' has been sent" in new Setup {
      val route   = routes.VersionedRoutingController.updateAssociationTTL(boxAssociation._id)
      val request = FakeRequest(route.method, route.url, FakeHeaders(Seq(Constants.APIVersionHeaderKey -> "final")), Json.toJson(validBody.sample.get))

      val result = controller.updateAssociationTTL(boxAssociation._id)(request)

      status(result) shouldBe NO_CONTENT
    }

  }

  "postNotificationByContentType" should {
    "call the transitional controller when APIVersion non 'final' value has been sent" in new Setup {
      val route                = routes.VersionedRoutingController.postNotificationByContentType(boxAssociation._id, messageId)
      val headers: FakeHeaders = FakeHeaders(Seq(Constants.APIVersionHeaderKey -> "anything", HeaderNames.CONTENT_TYPE -> MimeTypes.JSON))
      val request              = FakeRequest(route.method, route.url, headers, xmlStream)

      val result = controller.postNotificationByContentType(boxAssociation._id, messageId)(request)

      status(result) shouldBe ACCEPTED
      contentAsString(result) shouldBe "transitional"
    }

    "call the transitional controller when no APIVersion header has been sent" in new Setup {
      val route   = routes.VersionedRoutingController.postNotificationByContentType(boxAssociation._id, messageId)
      val request = FakeRequest(route.method, route.url, FakeHeaders(Seq.empty), xmlStream)

      val result = controller.postNotificationByContentType(boxAssociation._id, messageId)(request)

      status(result) shouldBe ACCEPTED
      contentAsString(result) shouldBe "transitional"
    }

    "call the versioned controller when APIVersion header 'final' has been sent" in new Setup {
      val route   = routes.VersionedRoutingController.postNotificationByContentType(boxAssociation._id, messageId)
      val request = FakeRequest(route.method, route.url, FakeHeaders(Seq(Constants.APIVersionHeaderKey -> "final")), xmlStream)

      val result = controller.postNotificationByContentType(boxAssociation._id, messageId)(request)

      status(result) shouldBe ACCEPTED
      contentAsString(result) shouldBe "final"
    }

  }

  "postNotification" should {
    "call the transitional controller when APIVersion non 'final' value has been sent" in new Setup {
      val route                = routes.VersionedRoutingController.postNotification(boxAssociation._id, messageId, NotificationType.SUBMISSION_NOTIFICATION)
      val headers: FakeHeaders = FakeHeaders(Seq(Constants.APIVersionHeaderKey -> "anything", HeaderNames.CONTENT_TYPE -> MimeTypes.JSON))
      val request              = FakeRequest(route.method, route.url, headers, xmlStream)

      val result = controller.postNotification(boxAssociation._id, messageId, NotificationType.SUBMISSION_NOTIFICATION)(request)

      status(result) shouldBe ACCEPTED
      contentAsString(result) shouldBe "transitional"
    }

    "call the transitional controller when no APIVersion header has been sent" in new Setup {
      val route   = routes.VersionedRoutingController.postNotification(boxAssociation._id, messageId, NotificationType.SUBMISSION_NOTIFICATION)
      val request = FakeRequest(route.method, route.url, FakeHeaders(Seq.empty), xmlStream)

      val result = controller.postNotification(boxAssociation._id, messageId, NotificationType.SUBMISSION_NOTIFICATION)(request)

      status(result) shouldBe ACCEPTED
      contentAsString(result) shouldBe "transitional"
    }

    "call the versioned controller when APIVersion header 'final' has been sent" in new Setup {
      val route   = routes.VersionedRoutingController.postNotification(boxAssociation._id, messageId, NotificationType.SUBMISSION_NOTIFICATION)
      val request = FakeRequest(route.method, route.url, FakeHeaders(Seq(Constants.APIVersionHeaderKey -> "final")), xmlStream)

      val result = controller.postNotification(boxAssociation._id, messageId, NotificationType.SUBMISSION_NOTIFICATION)(request)

      status(result) shouldBe ACCEPTED
      contentAsString(result) shouldBe "final"
    }

  }

  trait Setup {

    implicit val materializer: Materializer = Materializer(TestActorSystem.system)

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
        eori         <- Arbitrary.arbitrary[EORINumber]
      } yield JsonBody(clientId, movementType.movementType, eori.value)

    def findMovementType(t: String): Option[MovementType] = MovementType.values.find(
      movementType => movementType.movementType == t
    )

    val messageId = arbitraryMessageId.arbitrary.sample.get

    val xmlStream = Source.single(ByteString(<test>123</test>.mkString))

    implicit val mockClock: Clock = mock[Clock]

    val errorHandler = new DefaultHttpErrorHandler(HttpErrorConfig(showDevErrors = false, None), None, None)

    val boxAssociation = arbitraryBoxAssociation.arbitrary.sample.get

    private val mockAppConfig = mock[AppConfig]

    implicit val tfc: TemporaryFileCreator = SingletonTemporaryFileCreator

    val mockTransitionalPushPullNotificationService: TransitionalPushPullNotificationService   = mock[TransitionalPushPullNotificationService]
    val mockTransitionalMovementBoxAssociationRepository: TransitionalBoxAssociationRepository = mock[TransitionalBoxAssociationRepository]
    val mockTransitionalMovementBoxAssociationFactory: TransitionalBoxAssociationFactory       = mock[TransitionalBoxAssociationFactory]
    val mockTransitionalInternalAuthActionProvider: TransitionalInternalAuthActionProvider     = mock[TransitionalInternalAuthActionProvider]

    val mockFinalPushPullNotificationService: FinalPushPullNotificationService   = mock[FinalPushPullNotificationService]
    val mockFinalMovementBoxAssociationRepository: FinalBoxAssociationRepository = mock[FinalBoxAssociationRepository]
    val mockFinalMovementBoxAssociationFactory: FinalBoxAssociationFactory       = mock[FinalBoxAssociationFactory]
    val mockFinalInternalAuthActionProvider: FinalInternalAuthActionProvider     = mock[FinalInternalAuthActionProvider]

    val mockTransitionalPushNotificationController: TransitionalPushNotificationController = new TransitionalPushNotificationController(
      stubControllerComponents(),
      mockTransitionalPushPullNotificationService,
      mockTransitionalMovementBoxAssociationRepository,
      mockTransitionalMovementBoxAssociationFactory,
      mockTransitionalInternalAuthActionProvider
    ) {

      override def createBoxAssociation(movementId: MovementId): Action[JsValue] = Action.async(parse.json) {
        implicit request =>
          Future.successful(Created("transitional"))
      }

      override def updateAssociationTTL(movementId: MovementId): Action[AnyContent] = Action.async {
        implicit request =>
          Future.successful(NoContent)
      }

      override def postNotificationByContentType(movementId: MovementId, messageId: MessageId): Action[Source[ByteString, _]] = Action.async(streamFromMemory) {
        implicit request =>
          Future.successful(Accepted("transitional"))
      }

      override def postNotification(movementId: MovementId, messageId: MessageId, notificationType: NotificationType): Action[Source[ByteString, _]] =
        Action.async(streamFromMemory) {
          implicit request =>
            Future.successful(Accepted("transitional"))
        }

    }

    val mockFinalPushNotificationController: FinalPushNotificationController = new FinalPushNotificationController(
      stubControllerComponents(),
      mockFinalPushPullNotificationService,
      mockFinalMovementBoxAssociationRepository,
      mockFinalMovementBoxAssociationFactory,
      mockFinalInternalAuthActionProvider
    ) {

      override def createBoxAssociation(movementId: MovementId): Action[JsValue] = Action.async(parse.json) {
        implicit request =>
          Future.successful(Created("final"))
      }

      override def updateAssociationTTL(movementId: MovementId): Action[AnyContent] = Action.async {
        implicit request =>
          Future.successful(NoContent)
      }

      override def postNotificationByContentType(movementId: MovementId, messageId: MessageId): Action[Source[ByteString, _]] = Action.async(streamFromMemory) {
        implicit request =>
          Future.successful(Accepted("final"))
      }

      override def postNotification(movementId: MovementId, messageId: MessageId, notificationType: NotificationType): Action[Source[ByteString, _]] =
        Action.async(streamFromMemory) {
          implicit request =>
            Future.successful(Accepted("final"))
        }

    }

    val controller = new VersionedRoutingController(
      stubControllerComponents(),
      mockTransitionalPushNotificationController,
      mockFinalPushNotificationController
    )(materializer)

  }
}
