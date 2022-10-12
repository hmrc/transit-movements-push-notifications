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

package uk.gov.hmrc.transitmovementspushnotifications.controllers.errors

import akka.util.Timeout
import cats.syntax.all._
import org.scalatest.time.Millis
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import uk.gov.hmrc.transitmovementspushnotifications.base.SpecBase
import uk.gov.hmrc.transitmovementspushnotifications.controllers.errors.ErrorCode.BadRequest
import uk.gov.hmrc.transitmovementspushnotifications.controllers.errors.ErrorCode.InternalServerError
import uk.gov.hmrc.transitmovementspushnotifications.controllers.errors.HeaderExtractError.NoHeaderFound

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError._
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError.InvalidBoxId

import scala.concurrent.duration.DurationInt

class ConvertErrorSpec extends SpecBase {

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(2, Seconds), interval = Span(5, Millis))

  object Harness extends ConvertError

  import Harness._

  "Mongo error" - {

    "for a success" in {
      val input = Right[MongoError, Unit](()).toEitherT[Future]
      whenReady(input.asPresentation.value) {
        _ mustBe Right(())
      }
    }

    "for a failure" in {
      val exception = new Exception("mongo failure")
      Seq(
        UnexpectedError(Some(exception))       -> InternalServiceError("Internal server error", InternalServerError, Some(exception)),
        InsertNotAcknowledged("Insert failed") -> InternalServiceError("Insert failed", InternalServerError, None),
        DocumentNotFound("Movement not found") -> StandardError("Movement not found", ErrorCode.NotFound)
      ).foreach {
        mongoAndPresentationError =>
          val input = Left[MongoError, Unit](mongoAndPresentationError._1).toEitherT[Future]
          whenReady(input.asPresentation.value) {
            _ mustBe Left(mongoAndPresentationError._2)
          }
      }
    }
  }

  "Header extract error" - {

    "for a success" in {
      val input = Right[HeaderExtractError, Unit](()).toEitherT[Future]
      whenReady(input.asPresentation.value) {
        _ mustBe Right(())
      }
    }

    "for a failure" in {
      val input = Left[HeaderExtractError, Unit](NoHeaderFound("Missing header")).toEitherT[Future]
      whenReady(input.asPresentation.value) {
        _ mustBe Left(StandardError("Missing header", ErrorCode.BadRequest))
      }
    }
  }

  "PPNS error" - {

    "for a success" in {
      val input = Right[PushPullNotificationError, Unit](()).toEitherT[Future]
      whenReady(input.asPresentation.value) {
        _ mustBe Right(())
      }
    }

    "for a failure" in {
      val exception = new Exception("PPNS failure")
      Seq(
        PushPullNotificationError.UnexpectedError(Some(exception)) -> InternalServiceError("Internal server error", InternalServerError, Some(exception)),
        InvalidBoxId("Box id does not exist")                      -> StandardError("Box id does not exist", ErrorCode.BadRequest)
      ).foreach {
        ppnsAndPresentationError =>
          val input = Left[PushPullNotificationError, Unit](ppnsAndPresentationError._1).toEitherT[Future]
          whenReady(input.asPresentation.value) {
            _ mustBe Left(ppnsAndPresentationError._2)
          }
      }
    }
  }

}
