package uk.gov.hmrc.transitmovementspushnotifications.models.formats

import play.api.libs.json.Format
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import uk.gov.hmrc.mongo.play.json.formats.MongoBinaryFormats
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.mongo.play.json.formats.MongoUuidFormats
import uk.gov.hmrc.transitmovementspushnotifications.models.MovementBoxAssociation

import java.time.OffsetDateTime
import java.time.ZoneOffset

trait MongoFormats extends CommonFormats with MongoBinaryFormats.Implicits with MongoJavatimeFormats.Implicits with MongoUuidFormats.Implicits {

  implicit val offsetDateTimeReads: Reads[OffsetDateTime] = Reads {
    value =>
      jatLocalDateTimeFormat
        .reads(value)
        .map(
          localDateTime => localDateTime.atOffset(ZoneOffset.UTC)
        )
  }

  implicit val offsetDateTimeWrites: Writes[OffsetDateTime] = Writes {
    value => jatLocalDateTimeFormat.writes(value.toLocalDateTime)
  }

  implicit val offsetDateTimeFormat: Format[OffsetDateTime] = Format.apply(offsetDateTimeReads, offsetDateTimeWrites)

  // these use the dates above, so need to be here for compile-time macro expansion
  implicit val movementBoxAssociationFormat: Format[MovementBoxAssociation] = Json.format[MovementBoxAssociation]

}

object MongoFormats extends MongoFormats
