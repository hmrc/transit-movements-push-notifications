package uk.gov.hmrc.transitmovementspushnotifications.models

import java.time.OffsetDateTime

case class MovementBoxAssociation(boxId: BoxId, movementId: MovementId, updated: OffsetDateTime)
