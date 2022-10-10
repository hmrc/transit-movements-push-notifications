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

import cats.data.EitherT
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.api.mvc.Result
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.transitmovementspushnotifications.controllers.errors.ConvertError
import uk.gov.hmrc.transitmovementspushnotifications.controllers.errors.PresentationError
import uk.gov.hmrc.transitmovementspushnotifications.models.BoxId
import uk.gov.hmrc.transitmovementspushnotifications.models.MovementId
import uk.gov.hmrc.transitmovementspushnotifications.models.request.BoxAssociationRequest
import uk.gov.hmrc.transitmovementspushnotifications.repositories.MovementBoxAssociationRepository
import uk.gov.hmrc.transitmovementspushnotifications.services.MovementBoxAssociationFactory
import uk.gov.hmrc.transitmovementspushnotifications.services.PushPullNotificationService

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton()
class PushNotificationController @Inject() (
  cc: ControllerComponents,
  pushPullNotificationService: PushPullNotificationService,
  movementBoxAssociationRepository: MovementBoxAssociationRepository,
  movementBoxAssociationFactory: MovementBoxAssociationFactory
)(implicit
  ec: ExecutionContext
) extends BackendController(cc)
    with ConvertError {

  def createBoxAssociation(movementId: MovementId): Action[AnyContent] = Action.async {
    implicit request =>
      (for {
        boxId <- getBoxId(request.body.asJson)
        movementBoxAssociation = movementBoxAssociationFactory.create(boxId, movementId)
        result <- movementBoxAssociationRepository.insert(movementBoxAssociation).asPresentation
      } yield result).fold[Result](
        baseError => Status(baseError.code.statusCode)(Json.toJson(baseError)),
        _ => Accepted
      )
  }

  private def getBoxId(requestBodyOpt: Option[JsValue])(implicit hc: HeaderCarrier): EitherT[Future, PresentationError, BoxId] =
    requestBodyOpt
      .map {
        _.validate[BoxAssociationRequest] match {
          case JsSuccess(box, _) =>
            if (box.boxId.isDefined) pushPullNotificationService.checkBoxIdExists(box.boxId.get).asPresentation
            else pushPullNotificationService.getDefaultBoxId(box.clientId).asPresentation
        }
      }
      .getOrElse(
        Left(PresentationError.badRequestError("Expected clientId to be present in the body"))
      )
}
