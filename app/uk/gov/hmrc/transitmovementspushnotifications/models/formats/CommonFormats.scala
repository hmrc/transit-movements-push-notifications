package uk.gov.hmrc.transitmovementspushnotifications.models.formats

import cats.data.NonEmptyList
import play.api.libs.json.Format
import play.api.libs.json.JsError
import play.api.libs.json.JsResult
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.functional.syntax.toInvariantFunctorOps

object CommonFormats extends CommonFormats

trait CommonFormats {

  implicit def nonEmptyListFormat[A: Format]: Format[NonEmptyList[A]] =
    Format
      .of[List[A]]
      .inmap(
        NonEmptyList.fromListUnsafe,
        _.toList
      )

//  implicit val mrnFormat: Format[MovementReferenceNumber] = Json.valueFormat[MovementReferenceNumber]
//  implicit val eoriNumberFormat: Format[EORINumber]       = Json.valueFormat[EORINumber]
//  implicit val messageIdFormat: Format[MessageId]         = Json.valueFormat[MessageId]
//  implicit val departureIdFormat: Format[DepartureId]     = Json.valueFormat[DepartureId]

  def enumFormat[A](values: Set[A])(getKey: A => String): Format[A] = new Format[A] {

    override def writes(a: A): JsValue =
      JsString(getKey(a))

    override def reads(json: JsValue): JsResult[A] = json match {
      case JsString(str) =>
        values
          .find(getKey(_) == str)
          .map(JsSuccess(_))
          .getOrElse(JsError("error.expected.validenumvalue"))
      case _ =>
        JsError("error.expected.enumstring")
    }
  }
}
