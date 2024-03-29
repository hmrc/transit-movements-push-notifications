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
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess

class NotificationTypeSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  "NotificationType reads" - {
    NotificationType.values.foreach {
      notificationType =>
        s"should read a JsString of $notificationType as a valid notification type" in {
          NotificationType.notificationTypeReads.reads(JsString(notificationType.toString)) mustBe JsSuccess(notificationType)
        }
    }

    "should fail for any other string" in forAll(Gen.alphaNumStr) {
      string =>
        NotificationType.notificationTypeReads.reads(JsString(string)) mustBe JsError()
    }
  }

  "NotificationType writes" - {
    NotificationType.values.foreach {
      notificationType =>
        s"should write a JsString of $notificationType from a valid notification type" in {
          NotificationType.notificationTypeWrites.writes(notificationType) mustBe JsString(notificationType.toString)
        }
    }
  }

}
