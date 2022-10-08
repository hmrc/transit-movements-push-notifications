package uk.gov.hmrc.transitmovementspushnotifications.models

import play.api.libs.json.Json

case class BoxId(value: String) extends AnyVal

object BoxId {
  implicit val boxIdFormats = Json.format[BoxId]
}
