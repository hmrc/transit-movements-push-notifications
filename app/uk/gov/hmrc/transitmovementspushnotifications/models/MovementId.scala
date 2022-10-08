package uk.gov.hmrc.transitmovementspushnotifications.models

import play.api.libs.json.Json

case class MovementId(value: String) extends AnyVal

object MovementId {
  implicit val movementIdFormat = Json.format[MovementId]
}
