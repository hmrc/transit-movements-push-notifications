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

  val statusValues = Seq(SUBMISSION_NOTIFICATION, MESSAGE_RECEIVED)

  implicit val messageStatusWrites = new Writes[NotificationType] {

    def writes(notificationType: NotificationType) = Json.toJson(notificationType.toString())
  }

  implicit val statusReads: Reads[NotificationType] = Reads {
    case JsString("Received") => JsSuccess(SUBMISSION_NOTIFICATION)
    case JsString("Pending")  => JsSuccess(MESSAGE_RECEIVED)
    case _                    => JsError("Invalid notification type")
  }

}
