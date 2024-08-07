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
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import cats.data.EitherT
import play.api.Logging
import play.api.http.MimeTypes
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.internalauth.client.IAAction
import uk.gov.hmrc.internalauth.client.Predicate
import uk.gov.hmrc.internalauth.client.Resource
import uk.gov.hmrc.internalauth.client.ResourceLocation
import uk.gov.hmrc.internalauth.client.ResourceType
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.controllers.actions.InternalAuthActionProvider
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.controllers.errors.ConvertError
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.controllers.errors.PresentationError
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.controllers.stream.StreamingParsers
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MessageId
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MessageType
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MovementId
import uk.gov.hmrc.transitmovementspushnotifications.models.common.NotificationType
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.models.request.BoxAssociationRequest
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.models.responses.BoxResponse
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.repositories.BoxAssociationRepository
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.services.BoxAssociationFactory
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.services.PushPullNotificationService

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton()
class PushNotificationController @Inject() (
  cc: ControllerComponents,
  pushPullNotificationService: PushPullNotificationService,
  boxAssociationRepository: BoxAssociationRepository,
  boxAssociationFactory: BoxAssociationFactory,
  internalAuth: InternalAuthActionProvider
)(implicit
  val materializer: Materializer,
  ec: ExecutionContext,
  temporaryFileCreator: TemporaryFileCreator
) extends BackendController(cc)
    with ConvertError
    with ContentTypeRouting
    with Logging
    with StreamingParsers {

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

  def postNotificationByContentType(movementId: MovementId, messageId: MessageId): Action[Source[ByteString, _]] =
    contentTypeRoute {
      case Some(MimeTypes.XML) =>
        postNotification(movementId, messageId, NotificationType.MESSAGE_RECEIVED)
      case Some(MimeTypes.JSON) =>
        postNotification(movementId, messageId, NotificationType.SUBMISSION_NOTIFICATION)
      case None =>
        postNotification(movementId, messageId, NotificationType.MESSAGE_RECEIVED)
    }

  def postNotification(movementId: MovementId, messageId: MessageId, notificationType: NotificationType): Action[Source[ByteString, _]] =
    internalAuth(notificationPermission).async(streamFromMemory) {
      implicit request =>
        (for {
          boxAssociation <- boxAssociationRepository.getBoxAssociation(movementId).asPresentation
          messageType = hc
            .headers(Seq("X-Message-Type"))
            .headOption
            .map(
              x => MessageType(x._2)
            )
          source <- reUsableSource(request)
          size   <- calculateSize(source.lift(1).get)
          result <- pushPullNotificationService
            .sendPushNotification(boxAssociation, size, messageId, source.lift(2).get, notificationType, messageType)
            .asPresentation
        } yield result).fold[Result](
          baseError => Status(baseError.code.statusCode)(Json.toJson(baseError)),
          _ => Accepted
        )

    }

  def updateAssociationTTL(movementId: MovementId): Action[AnyContent] = internalAuth(associatePermission).async {
    boxAssociationRepository
      .update(movementId)
      .asPresentation
      .fold(
        baseError => Status(baseError.code.statusCode)(Json.toJson(baseError)),
        _ => NoContent
      )
  }

  def createBoxAssociation(movementId: MovementId): Action[JsValue] = internalAuth(associatePermission).async(parse.json) {
    implicit request =>
      (for {
        boxAssociation <- getBoxAssociationRequest(request.body)
        boxId          <- pushPullNotificationService.getBoxId(boxAssociation).asPresentation
        movementBoxAssociation = boxAssociationFactory.create(boxId, movementId, boxAssociation.movementType, boxAssociation.enrollmentEORINumber)
        result <- boxAssociationRepository.insert(movementBoxAssociation).asPresentation
      } yield result).fold[Result](
        baseError => Status(baseError.code.statusCode)(Json.toJson(baseError)),
        result => Created(Json.toJson(BoxResponse(result.boxId)))
      )
  }

  private def getBoxAssociationRequest(body: JsValue): EitherT[Future, PresentationError, BoxAssociationRequest] =
    body
      .validate[BoxAssociationRequest] match {
      case JsSuccess(boxAssociation, _) => EitherT.rightT[Future, PresentationError](boxAssociation)
      case _ =>
        EitherT.leftT[Future, BoxAssociationRequest](
          PresentationError.badRequestError("Expected clientId, movementType and enrollmentEORINumber to be present in the body")
        )
    }

  private def materializeSource(source: Source[ByteString, _]): EitherT[Future, PresentationError, Seq[ByteString]] =
    EitherT(
      source
        .runWith(Sink.seq)
        .map(Right(_): Either[PresentationError, Seq[ByteString]])
        .recover {
          error =>
            Left(PresentationError.internalServerError(cause = Some(error)))
        }
    )
  // Function to create a new source from the materialized sequence
  private def createReusableSource(seq: Seq[ByteString]): Source[ByteString, _] = Source(seq.toList)

  private def reUsableSource(request: Request[Source[ByteString, _]]): EitherT[Future, PresentationError, List[Source[ByteString, _]]] = for {
    byteStringSeq <- materializeSource(request.body)
  } yield List.fill(3)(createReusableSource(byteStringSeq))

  // Function to calculate the size using EitherT
  private def calculateSize(source: Source[ByteString, _]): EitherT[Future, PresentationError, Long] = {
    val sizeFuture: Future[Either[PresentationError, Long]] = source
      .map(_.size.toLong)
      .runWith(Sink.fold(0L)(_ + _))
      .map(
        size => Right(size): Either[PresentationError, Long]
      )
      .recover {
        case _: Exception => Left(PresentationError.internalServerError())
      }

    EitherT(sizeFuture)
  }

}
