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
import cats.data.EitherT
import play.api.Logging
import play.api.http.MimeTypes
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.transitmovementspushnotifications.controllers.errors.ConvertError
import uk.gov.hmrc.transitmovementspushnotifications.controllers.errors.PresentationError
import uk.gov.hmrc.transitmovementspushnotifications.controllers.stream.StreamingParsers
import uk.gov.hmrc.transitmovementspushnotifications.models.MessageId
import uk.gov.hmrc.transitmovementspushnotifications.models.MovementId
import uk.gov.hmrc.transitmovementspushnotifications.models.NotificationType
import uk.gov.hmrc.transitmovementspushnotifications.models.request.BoxAssociationRequest
import uk.gov.hmrc.transitmovementspushnotifications.models.responses.BoxResponse
import uk.gov.hmrc.transitmovementspushnotifications.repositories.BoxAssociationRepository
import uk.gov.hmrc.transitmovementspushnotifications.services.BoxAssociationFactory
import uk.gov.hmrc.transitmovementspushnotifications.services.PushPullNotificationService

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton()
class PushNotificationController @Inject() (
  cc: ControllerComponents,
  pushPullNotificationService: PushPullNotificationService,
  boxAssociationRepository: BoxAssociationRepository,
  boxAssociationFactory: BoxAssociationFactory
)(implicit
  val materializer: Materializer,
  ec: ExecutionContext,
  temporaryFileCreator: TemporaryFileCreator
) extends BackendController(cc)
    with ConvertError
    with ContentTypeRouting
    with Logging
    with StreamingParsers {

  def postNotification(movementId: MovementId, messageId: MessageId): Action[Source[ByteString, _]] =
    contentTypeRoute {
      case Some(MimeTypes.XML) =>
        postNotification(movementId, messageId, NotificationType.MESSAGE_RECEIVED)
      case Some(MimeTypes.JSON) =>
        postNotification(movementId, messageId, NotificationType.SUBMISSION_NOTIFICATION)
      case None =>
        postNotification(movementId, messageId, NotificationType.MESSAGE_RECEIVED)
    }

  private def postNotification(movementId: MovementId, messageId: MessageId, notificationType: NotificationType): Action[Source[ByteString, _]] =
    Action.streamWithSize {

      implicit request => size =>
        (for {
          boxAssociation <- boxAssociationRepository.getBoxAssociation(movementId).asPresentation
          result <- pushPullNotificationService
            .sendPushNotification(boxAssociation, size, messageId, request.body, notificationType)
            .asPresentation
        } yield result).fold[Result](
          baseError => Status(baseError.code.statusCode)(Json.toJson(baseError)),
          _ => Accepted
        )

    }

  def updateAssociationTTL(movementId: MovementId): Action[AnyContent] = Action.async {
    boxAssociationRepository
      .update(movementId)
      .asPresentation
      .fold(
        baseError => Status(baseError.code.statusCode)(Json.toJson(baseError)),
        _ => NoContent
      )
  }

  def createBoxAssociation(movementId: MovementId): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      (for {
        boxAssociation <- getBoxAssociationRequest(request.body)
        boxId          <- pushPullNotificationService.getBoxId(boxAssociation).asPresentation
        movementBoxAssociation = boxAssociationFactory.create(boxId, movementId, boxAssociation.movementType)
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
      case _                            => EitherT.leftT[Future, BoxAssociationRequest](PresentationError.badRequestError("Expected clientId and movementType to be present in the body"))
    }

}
