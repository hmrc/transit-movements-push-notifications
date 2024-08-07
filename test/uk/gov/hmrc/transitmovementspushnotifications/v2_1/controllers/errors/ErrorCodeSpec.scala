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

package uk.gov.hmrc.transitmovementspushnotifications.v2_1.controllers.errors

import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import uk.gov.hmrc.transitmovementspushnotifications.v2_1.base.SpecBase

class ErrorCodeSpec extends SpecBase {

  "writes" in {
    ErrorCode.errorCodes.foreach {
      errorCode =>
        val json = JsString(errorCode.code)

        json.validate[ErrorCode] match {
          case JsSuccess(code, _) => code mustBe errorCode
          case _                  => fail("failed to match error code")
        }
    }
  }
}
