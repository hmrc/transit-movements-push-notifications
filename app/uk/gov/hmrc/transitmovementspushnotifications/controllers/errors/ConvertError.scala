package uk.gov.hmrc.transitmovementspushnotifications.controllers.errors

import cats.data.EitherT
import uk.gov.hmrc.transitmovementspushnotifications.connectors.PushPullNotificationConnector
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError

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
    import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError._

    def convert(mongoError: MongoError): PresentationError = mongoError match {
      case UnexpectedError(ex)            => PresentationError.internalServiceError(cause = ex)
      case InsertNotAcknowledged(message) => PresentationError.internalServiceError(message = message)
      case UpdateNotAcknowledged(message) => PresentationError.internalServiceError(message = message)
      case DocumentNotFound(message)      => PresentationError.notFoundError(message = message)
    }
  }

  implicit val headerExtractErrorConverter = new Converter[HeaderExtractError] {
    import uk.gov.hmrc.transitmovementspushnotifications.controllers.errors.HeaderExtractError._

    def convert(headerExtractError: HeaderExtractError): PresentationError = headerExtractError match {
      case NoHeaderFound(message)      => PresentationError.badRequestError(message)
      case InvalidMessageType(message) => PresentationError.badRequestError(message)
    }
  }

  implicit val ppnsErrorConverter = new Converter[PushPullNotificationError] {
    import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError._

    def convert(headerExtractError: PushPullNotificationError): PresentationError = headerExtractError match {
      case UnexpectedError(ex) => PresentationError.internalServiceError(cause = ex)
      case InvalidBoxId(msg)   => PresentationError.badRequestError(message = msg)
    }
  }

}
