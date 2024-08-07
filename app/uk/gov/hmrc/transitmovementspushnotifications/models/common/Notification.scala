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

package uk.gov.hmrc.transitmovementspushnotifications.models.common

import play.api.libs.json._
import uk.gov.hmrc.transitmovementspushnotifications.models.common.EORINumber
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MessageId
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MessageType
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MovementId
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MovementType

sealed trait Notification extends Product with Serializable {
  @transient val movementId: MovementId
  @transient val movementType: MovementType
  val messageUri: String
  val messageId: MessageId
  val notificationType: NotificationType
  val enrollmentEORINumber: Option[EORINumber]
}

case class MessageReceivedNotification(
  messageUri: String,
  messageId: MessageId,
  movementId: MovementId,
  movementType: MovementType,
  enrollmentEORINumber: Option[EORINumber],
  messageType: Option[MessageType],
  messageBody: Option[String]
) extends Notification {
  override val notificationType: NotificationType = NotificationType.MESSAGE_RECEIVED
}

case class SubmissionNotification(
  messageUri: String,
  messageId: MessageId,
  movementId: MovementId,
  movementType: MovementType,
  enrollmentEORINumber: Option[EORINumber],
  response: Option[JsValue]
) extends Notification {
  override val notificationType: NotificationType = NotificationType.SUBMISSION_NOTIFICATION
}

object Notification {

  private def movementId(movementId: MovementId, movementType: MovementType): JsObject =
    if (movementType == MovementType.Departure) Json.obj("departureId" -> movementId.value)
    else Json.obj("arrivalId"                                          -> movementId.value)

  private def enrollmentEORINumber(enrollmentEORINumber: Option[EORINumber]): JsObject =
    enrollmentEORINumber
      .map(
        eori => Json.obj("enrollmentEORINumber" -> eori.value)
      )
      .getOrElse(JsObject.empty)

  private val messageReceivedNotificationWrites: OWrites[MessageReceivedNotification] = OWrites {
    notification =>
      notification.messageType
        .map(
          x => Json.obj("messageType" -> x)
        )
        .getOrElse(Json.obj()) ++
        notification.messageBody
          .map(
            x => Json.obj("messageBody" -> x)
          )
          .getOrElse(Json.obj())
  }

  private val submissionNotificationWrites: OWrites[SubmissionNotification] = OWrites {
    notification =>
      notification.response
        .map(
          x => Json.obj("response" -> x)
        )
        .getOrElse(Json.obj())
  }

  implicit val notificationWrites: OWrites[Notification] = OWrites[Notification] {
    notification =>
      Json.obj(
        "messageUri"       -> notification.messageUri,
        "notificationType" -> notification.notificationType.toString,
        "messageId"        -> notification.messageId.value
      ) ++
        enrollmentEORINumber(notification.enrollmentEORINumber) ++
        movementId(notification.movementId, notification.movementType) ++
        (notification match {
          case x: MessageReceivedNotification => messageReceivedNotificationWrites.writes(x)
          case x: SubmissionNotification      => submissionNotificationWrites.writes(x)
        })
  }
}
