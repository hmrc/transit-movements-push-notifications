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
import uk.gov.hmrc.transitmovementspushnotifications.models.common.NotificationType

class BindingsSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  "notificationTypeBinding" - {
    "bind" - {
      "when the specified type exists" in forAll(Gen.oneOf(NotificationType.values)) {
        notificationType =>
          Bindings.notificationTypeBinding.bind("ignored", notificationType.pathRepresentation) mustBe Right(notificationType)
      }

      "when the specified type does not exist" in forAll(Gen.stringOfN(2, Gen.alphaChar)) {
        notificationType =>
          Bindings.notificationTypeBinding.bind("ignored", notificationType) mustBe Left(s"Unable to find notification type of $notificationType")
      }
    }

    "unbind return the path representation" in forAll(Gen.oneOf(NotificationType.values)) {
      notificationType =>
        Bindings.notificationTypeBinding.unbind("ignored", notificationType) mustBe notificationType.pathRepresentation
    }
  }

}
