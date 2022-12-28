/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.transitmovementspushnotifications.connectors

import akka.util.Timeout
import org.mockito.Mockito.when
import org.scalacheck.Gen
import org.scalatest.exceptions.TestFailedException
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.libs.json.JsResult
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.test.Helpers.await
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.transitmovementspushnotifications.base.SpecBase

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class BaseConnectorSpec extends SpecBase {

  implicit val timeout: Timeout = 5.seconds

  object Harness extends BaseConnector

  "HttpResponseHelpers" - new BaseConnector() {

    case class TestObject(string: String, int: Int)

    implicit val reads: Reads[TestObject] = Json.reads[TestObject]

    "successfully converting a relevant object using 'as' returns the object" in {
      val sut = mock[HttpResponse]
      when(sut.json).thenReturn(
        Json.obj(
          "string" -> "string",
          "int"    -> 1
        )
      )

      whenReady(sut.as[TestObject]) {
        _ mustBe TestObject("string", 1)
      }
    }

    "unsuccessfully converting an object using 'as' returns a failed future" in {
      val sut = mock[HttpResponse]
      when(sut.json).thenReturn(
        Json.obj(
          "no"  -> "string",
          "no2" -> 1
        )
      )

      val result = sut
        .as[TestObject]
        .map(
          _ => fail("This should have failed")
        )
        .recover {
          case _: JsResult.Exception  => // success
          case x: TestFailedException => x
          case thr                    => fail(s"Test failed in an unexpected way: $thr")
        }

      // we just want the future to complete
      await(result)
    }

    "error returns a simple upstream error response" in {
      val expected = Gen.alphaNumStr.sample.value
      val sut      = mock[HttpResponse]
      when(sut.body).thenReturn(expected)
      when(sut.status).thenReturn(INTERNAL_SERVER_ERROR)

      val result = sut
        .error[TestObject]
        .map(
          _ => fail("This should have failed")
        )
        .recover {
          case UpstreamErrorResponse(`expected`, INTERNAL_SERVER_ERROR, _, _) => // success
          case x: TestFailedException                                         => x
          case thr =>
            fail(s"Test failed in an unexpected way: $thr")
        }

      await(result)
    }

  }

}
