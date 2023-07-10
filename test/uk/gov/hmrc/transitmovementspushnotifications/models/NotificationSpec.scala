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

import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.JsError
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json

class NotificationSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks {
  private val gen = Gen.listOfN(10, Gen.alphaChar).map(_.mkString)

  val validJSONBody = Json.toJson(
    Json.obj(
      "code" -> "SUCCESS",
      "message" ->
        s"The message for movement was successfully processed"
    )
  )

  "when MessageReceivedNotification JsObject is deserialized, return a MessageReceivedNotification" in forAll(gen, gen) {
    (messageUri, messageBody) =>
      val actual = Notification.messageReceivedNotificationFormat.reads(
        Json.obj("messageUri" -> messageUri, "notificationType" -> "MESSAGE_RECEIVED", "messageBody" -> messageBody)
      )
      val expected = MessageReceivedNotification(messageUri, None, Some(messageBody))
      actual mustBe JsSuccess(expected)
  }

  "when SubmissionNotification JsObject is deserialized, return a SubmissionNotification" in forAll(gen) {
    messageUri =>
      val actual = Notification.submissionNotificationFormat.reads(
        Json.obj("messageUri" -> messageUri, "notificationType" -> "SUBMISSION_NOTIFICATION", "response" -> validJSONBody)
      )
      val expected = SubmissionNotification(messageUri, Some(validJSONBody))
      actual mustBe JsSuccess(expected)
  }

  "when Notification JsObject is deserialized for MESSAGE_RECEIVED, return a MessageReceivedNotification" in forAll(gen, gen, Gen.option(gen)) {
    (messageUri, messageBody, messageTypeMaybe) =>
      val actual = Notification.notificationReads.reads(
        Json.obj("messageUri" -> messageUri, "notificationType" -> "MESSAGE_RECEIVED", "messageBody" -> messageBody) ++
          messageTypeMaybe
            .map(
              x => Json.obj("messageType" -> x)
            )
            .getOrElse(Json.obj())
      )
      val expected = MessageReceivedNotification(messageUri, messageTypeMaybe.map(MessageType.apply), Some(messageBody))
      actual mustBe JsSuccess(expected)
  }

  "when Notification JsObject is deserialized for SUBMISSION_NOTIFICATION, return a SubmissionNotification" in forAll(gen, Gen.option(gen)) {
    (messageUri, messageTypeMaybe) =>
      val actual = Notification.notificationReads.reads(
        Json.obj("messageUri" -> messageUri, "notificationType" -> "SUBMISSION_NOTIFICATION", "response" -> validJSONBody) ++
          messageTypeMaybe
            .map(
              x => Json.obj("messageType" -> x)
            )
            .getOrElse(Json.obj())
      )
      val expected = SubmissionNotification(messageUri, Some(validJSONBody))
      actual mustBe JsSuccess(expected)
  }

  "when Notification JsObject is deserialized for unknown Notification type, return a JsError" in forAll(gen) {
    messageUri =>
      val actual = Notification.notificationReads.reads(
        Json.obj("messageUri" -> messageUri, "notificationType" -> "unknown", "response" -> validJSONBody)
      )
      actual mustBe JsError("Invalid")
  }

  "when MessageReceivedNotification is serialized, return an appropriate JsObject" in forAll(gen, gen) {
    (messageUri, messageBody) =>
      val actual   = Notification.notificationWrites.writes(MessageReceivedNotification(messageUri, None, Some(messageBody)))
      val expected = Json.obj("messageUri" -> messageUri, "notificationType" -> "MESSAGE_RECEIVED", "messageBody" -> messageBody)
      actual mustBe expected
  }

  "when SubmissionNotification is serialized, return an appropriate JsObject" in forAll(gen) {
    messageUri =>
      val actual   = Notification.notificationWrites.writes(SubmissionNotification(messageUri, Some(validJSONBody)))
      val expected = Json.obj("messageUri" -> messageUri, "notificationType" -> "SUBMISSION_NOTIFICATION", "response" -> validJSONBody)
      actual mustBe expected
  }

}
