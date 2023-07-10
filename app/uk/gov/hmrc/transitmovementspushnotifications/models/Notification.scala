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

package uk.gov.hmrc.transitmovementspushnotifications.models

import play.api.libs.json._

sealed trait Notification extends Product with Serializable {
  val messageUri: String
  val notificationType: NotificationType
}

case class MessageReceivedNotification(messageUri: String, messageType: Option[MessageType], messageBody: Option[String]) extends Notification {
  override val notificationType: NotificationType = NotificationType.MESSAGE_RECEIVED
}

case class SubmissionNotification(messageUri: String, response: Option[JsValue]) extends Notification {
  override val notificationType: NotificationType = NotificationType.SUBMISSION_NOTIFICATION
}

object Notification {
  implicit val messageReceivedNotificationFormat: OFormat[MessageReceivedNotification] = Json.format[MessageReceivedNotification]
  implicit val submissionNotificationFormat: OFormat[SubmissionNotification]           = Json.format[SubmissionNotification]

  implicit val notificationReads: Reads[Notification] = Reads[Notification] {
    jsValue =>
      jsValue \ "notificationType" match {
        case JsDefined(JsString("MESSAGE_RECEIVED"))        => messageReceivedNotificationFormat.reads(jsValue)
        case JsDefined(JsString("SUBMISSION_NOTIFICATION")) => submissionNotificationFormat.reads(jsValue)
        case _                                              => JsError("Invalid")
      }
  }

  implicit val notificationWrites: OWrites[Notification] = OWrites[Notification] {
    notification =>
      Json.obj("notificationType" -> notification.notificationType) ++ (notification match {
        case x: MessageReceivedNotification => messageReceivedNotificationFormat.writes(x)
        case x: SubmissionNotification      => submissionNotificationFormat.writes(x)
      })
  }
}
