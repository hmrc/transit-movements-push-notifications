package uk.gov.hmrc.transitmovementspushnotifications.services

import akka.stream.Materializer
import cats.data.NonEmptyList
import com.google.inject.ImplementedBy
import uk.gov.hmrc.transitmovementspushnotifications.models.BoxId
import uk.gov.hmrc.transitmovementspushnotifications.models.MovementBoxAssociation
import uk.gov.hmrc.transitmovementspushnotifications.models.MovementId

import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.inject.Inject

@ImplementedBy(classOf[MovementBoxAssociationFactoryImpl])
trait MovementBoxAssociationFactory {
  def create(boxId: BoxId, movementId: MovementId): MovementBoxAssociation
}

class MovementBoxAssociationFactoryImpl @Inject() (
  clock: Clock
)(implicit
  val materializer: Materializer
) extends MovementBoxAssociationFactory {

  def create(
    boxId: BoxId,
    movementId: MovementId
  ): MovementBoxAssociation =
    MovementBoxAssociation(
      boxId = boxId,
      movementId = movementId,
      updated = OffsetDateTime.ofInstant(clock.instant, ZoneOffset.UTC)
    )

}
