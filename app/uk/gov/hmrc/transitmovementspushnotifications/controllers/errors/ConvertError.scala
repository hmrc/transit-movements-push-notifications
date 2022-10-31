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

import cats.data.EitherT
import uk.gov.hmrc.transitmovementspushnotifications.controllers.errors.HeaderExtractError.NoHeaderFound
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.BoxNotFound
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.InvalidBoxId
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.UnexpectedError
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError.DocumentNotFound
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError.InsertNotAcknowledged

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait ConvertError {

  implicit class ErrorConverter[E, A](value: EitherT[Future, E, A]) {

    def asPresentation(implicit c: Converter[E], ec: ExecutionContext): EitherT[Future, PresentationError, A] =
      value.leftMap(c.convert)
  }

  sealed trait Converter[E] {
    def convert(input: E): PresentationError
  }

  implicit val mongoErrorConverter = new Converter[MongoError] {

    def convert(mongoError: MongoError): PresentationError = mongoError match {
      case MongoError.UnexpectedError(ex) => PresentationError.internalServerError(cause = ex)
      case InsertNotAcknowledged(message) => PresentationError.internalServerError(message = message)
      case DocumentNotFound(message)      => PresentationError.notFoundError(message = message)
    }
  }

  implicit val headerExtractErrorConverter = new Converter[HeaderExtractError] {

    def convert(headerExtractError: HeaderExtractError): PresentationError = headerExtractError match {
      case NoHeaderFound(message) => PresentationError.badRequestError(message)
    }
  }

  implicit val ppnsErrorConverter = new Converter[PushPullNotificationError] {

    def convert(pushPullNotificationError: PushPullNotificationError): PresentationError = pushPullNotificationError match {
      case UnexpectedError(ex) => PresentationError.internalServerError(cause = ex)
      case InvalidBoxId        => PresentationError.internalServerError()
      case BoxNotFound(msg)    => PresentationError.notFoundError(msg)
    }
  }

}
