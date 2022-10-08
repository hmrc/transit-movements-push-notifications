package uk.gov.hmrc.transitmovementspushnotifications.services.errors

sealed abstract class MongoError

object MongoError {
  case class UnexpectedError(exception: Option[Throwable] = None) extends MongoError
  case class InsertNotAcknowledged(message: String)               extends MongoError
  case class UpdateNotAcknowledged(message: String)               extends MongoError
  case class DocumentNotFound(message: String)                    extends MongoError
}
