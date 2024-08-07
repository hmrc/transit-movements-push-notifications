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

package uk.gov.hmrc.transitmovementspushnotifications.v2_1.models

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import uk.gov.hmrc.transitmovementspushnotifications.generators.ModelGenerators
import uk.gov.hmrc.transitmovementspushnotifications.models.common.EORINumber
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MessageId
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MessageReceivedNotification
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MessageType
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MovementId
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MovementType
import uk.gov.hmrc.transitmovementspushnotifications.models.common.Notification
import uk.gov.hmrc.transitmovementspushnotifications.models.common.SubmissionNotification

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
      // We can only have six entries for forAll, so we have to manually add our seventh here
      val enrollmentEORINumberMaybe = Gen.option(arbitrary[EORINumber]).sample.get
      val actual = Notification.notificationWrites.writes(
        MessageReceivedNotification(
          messageUri,
          messageId,
          movementId,
          movementType,
          enrollmentEORINumberMaybe,
          Some(MessageType(messageType)),
          Some(messageBody)
        )
      )
      val expected = Json.obj(
        "messageUri"                              -> messageUri,
        "notificationType"                        -> "MESSAGE_RECEIVED",
        "messageType"                             -> messageType,
        s"${movementType.toString.toLowerCase}Id" -> movementId.value,
        "messageId"                               -> messageId.value,
        "messageBody"                             -> messageBody
      ) ++ enrollmentEORINumberMaybe
        .map(
          x => Json.obj("enrollmentEORINumber" -> x.value)
        )
        .getOrElse(JsObject.empty)
      actual mustBe expected
  }

  "when SubmissionNotification is serialized, return an appropriate JsObject" in forAll(
    gen,
    gen,
    arbitrary[MovementId],
    arbitrary[MessageId],
    arbitrary[MovementType],
    Gen.option(arbitrary[EORINumber])
  ) {
    (messageUri, messageBody, movementId, messageId, movementType, enrollmentEORINumberMaybe) =>
      val actual =
        Notification.notificationWrites.writes(
          SubmissionNotification(messageUri, messageId, movementId, movementType, enrollmentEORINumberMaybe, Some(Json.obj("test" -> messageBody)))
        )
      val expected = Json.obj(
        "messageUri"                              -> messageUri,
        "notificationType"                        -> "SUBMISSION_NOTIFICATION",
        s"${movementType.toString.toLowerCase}Id" -> movementId.value,
        "messageId"                               -> messageId.value,
        "response"                                -> Json.obj("test" -> messageBody)
      ) ++ enrollmentEORINumberMaybe
        .map(
          x => Json.obj("enrollmentEORINumber" -> x.value)
        )
        .getOrElse(JsObject.empty)
      actual mustBe expected
  }

}
