package uk.gov.hmrc.transitmovementspushnotifications.controllers

import cats.data.EitherT
import play.api.mvc.Headers
import uk.gov.hmrc.transitmovementspushnotifications.controllers.errors.HeaderExtractError
import uk.gov.hmrc.transitmovementspushnotifications.controllers.errors.HeaderExtractError.NoHeaderFound

import scala.concurrent.Future

trait HeaderValueExtractor {

  def extract(headers: Headers, headerKey: String): EitherT[Future, HeaderExtractError, String] =
    EitherT {
      headers.get(headerKey) match {
        case None              => Future.successful(Left(NoHeaderFound(s"Missing $headerKey header value")))
        case Some(headerValue) => Future.successful(Right(headerValue))
      }
    }

}
