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
import play.api.Logging
import play.api.libs.json.JsValue
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.transitmovementspushnotifications.config.Constants
import uk.gov.hmrc.transitmovementspushnotifications.controllers.stream.StreamingParsers
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MessageId
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MovementId
import uk.gov.hmrc.transitmovementspushnotifications.models.common.NotificationType
import uk.gov.hmrc.transitmovementspushnotifications.controllers.{PushNotificationController => TransitionalPushNotificationController}
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.controllers.{PushNotificationController => FinalPushNotificationController}

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class VersionedRoutingController @Inject() (
  cc: ControllerComponents,
  transitionalController: TransitionalPushNotificationController,
  finalController: FinalPushNotificationController
)(implicit val materializer: Materializer)
    extends BackendController(cc)
    with Logging
    with StreamingParsers {

  def createBoxAssociation(movementId: MovementId): Action[JsValue] =
    Action.async(parse.json) {
      implicit request =>
        request.headers.get(Constants.APIVersionHeaderKey).map(_.trim.toLowerCase) match {
          case Some(Constants.APIVersionFinalHeaderValue) =>
            finalController.createBoxAssociation(movementId)(request)
          case _ =>
            transitionalController.createBoxAssociation(movementId)(request)
        }
    }

  def updateAssociationTTL(movementId: MovementId): Action[AnyContent] =
    Action.async {
      implicit request =>
        request.headers.get(Constants.APIVersionHeaderKey).map(_.trim.toLowerCase) match {
          case Some(Constants.APIVersionFinalHeaderValue) =>
            finalController.updateAssociationTTL(movementId)(request)
          case _ =>
            transitionalController.updateAssociationTTL(movementId)(request)
        }
    }

  def postNotificationByContentType(movementId: MovementId, messageId: MessageId): Action[Source[ByteString, _]] =
    Action.async(streamFromMemory) {
      implicit request =>
        request.headers.get(Constants.APIVersionHeaderKey).map(_.trim.toLowerCase) match {
          case Some(Constants.APIVersionFinalHeaderValue) =>
            finalController.postNotificationByContentType(movementId, messageId)(request)
          case _ =>
            transitionalController.postNotificationByContentType(movementId, messageId)(request)
        }
    }

  def postNotification(movementId: MovementId, messageId: MessageId, notificationType: NotificationType): Action[Source[ByteString, _]] =
    Action.async(streamFromMemory) {
      implicit request =>
        request.headers.get(Constants.APIVersionHeaderKey).map(_.trim.toLowerCase) match {
          case Some(Constants.APIVersionFinalHeaderValue) =>
            finalController.postNotification(movementId, messageId, notificationType)(request)
          case _ =>
            transitionalController.postNotification(movementId, messageId, notificationType)(request)
        }
    }

}
