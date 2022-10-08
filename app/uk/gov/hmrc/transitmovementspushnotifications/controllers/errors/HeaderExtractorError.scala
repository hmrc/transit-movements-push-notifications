package uk.gov.hmrc.transitmovementspushnotifications.controllers.errors

sealed abstract class HeaderExtractError

object HeaderExtractError {
  case class NoHeaderFound(element: String)      extends HeaderExtractError
  case class InvalidMessageType(element: String) extends HeaderExtractError
}
