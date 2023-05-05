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

import play.api.libs.json.JsError
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.libs.json.Writes

sealed trait NotificationType

object NotificationType {
  final case object SUBMISSION_NOTIFICATION extends NotificationType

  final case object MESSAGE_RECEIVED extends NotificationType

  val values = Seq(SUBMISSION_NOTIFICATION, MESSAGE_RECEIVED)

  implicit val notificationTypeWrites = new Writes[NotificationType] {

    def writes(notificationType: NotificationType) = Json.toJson(notificationType.toString())
  }

  implicit val notificationTypeReads: Reads[NotificationType] = Reads {
    case JsString("SUBMISSION_NOTIFICATION") => JsSuccess(SUBMISSION_NOTIFICATION)
    case JsString("MESSAGE_RECEIVED")        => JsSuccess(MESSAGE_RECEIVED)
    case _                                   => JsError()
  }

}
