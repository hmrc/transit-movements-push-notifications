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

import play.api.libs.json.JsError
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.libs.json.Writes

import scala.collection.Seq

sealed trait NotificationType {
  def pathRepresentation: String
}

object NotificationType {

  final case object SUBMISSION_NOTIFICATION extends NotificationType {
    override def pathRepresentation: String = "submissionNotification"
  }

  final case object MESSAGE_RECEIVED extends NotificationType {
    override def pathRepresentation: String = "messageReceived"
  }

  val values: Seq[NotificationType] = Seq(SUBMISSION_NOTIFICATION, MESSAGE_RECEIVED)

  implicit val notificationTypeWrites: Writes[NotificationType] = (notificationType: NotificationType) => Json.toJson(notificationType.toString)

  implicit val notificationTypeReads: Reads[NotificationType] = Reads {
    case JsString("SUBMISSION_NOTIFICATION") => JsSuccess(SUBMISSION_NOTIFICATION)
    case JsString("MESSAGE_RECEIVED")        => JsSuccess(MESSAGE_RECEIVED)
    case _                                   => JsError()
  }

  def findByPathRepresentation(value: String): Option[NotificationType] = values.find(_.pathRepresentation.equalsIgnoreCase(value))

}
