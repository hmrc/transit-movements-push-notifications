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

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.Json
import uk.gov.hmrc.transitmovementspushnotifications.generators.ModelGenerators

class NotificationSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks with ModelGenerators {
  private val gen = Gen.stringOfN(10, Gen.alphaChar)

  "when MessageReceivedNotification is serialized for a departure, return an appropriate JsObject" in forAll(
    gen,
    gen,
    arbitrary[MovementId],
    arbitrary[MessageId],
    gen,
    arbitrary[MovementType]
  ) {
    (messageUri, messageBody, movementId, messageId, messageType, movementType) =>
      val actual = Notification.notificationWrites.writes(
        MessageReceivedNotification(messageUri, messageId, movementId, movementType, Some(MessageType(messageType)), Some(messageBody))
      )
      val expected = Json.obj(
        "messageUri"                              -> messageUri,
        "notificationType"                        -> "MESSAGE_RECEIVED",
        "messageType"                             -> messageType,
        s"${movementType.toString.toLowerCase}Id" -> movementId.value,
        "messageId"                               -> messageId.value,
        "messageBody"                             -> messageBody
      )
      actual mustBe expected
  }

  "when SubmissionNotification is serialized, return an appropriate JsObject" in forAll(
    gen,
    gen,
    arbitrary[MovementId],
    arbitrary[MessageId],
    arbitrary[MovementType]
  ) {
    (messageUri, messageBody, movementId, messageId, movementType) =>
      val actual =
        Notification.notificationWrites.writes(SubmissionNotification(messageUri, messageId, movementId, movementType, Some(Json.obj("test" -> messageBody))))
      val expected = Json.obj(
        "messageUri"                              -> messageUri,
        "notificationType"                        -> "SUBMISSION_NOTIFICATION",
        s"${movementType.toString.toLowerCase}Id" -> movementId.value,
        "messageId"                               -> messageId.value,
        "response"                                -> Json.obj("test" -> messageBody)
      )
      actual mustBe expected
  }

}
