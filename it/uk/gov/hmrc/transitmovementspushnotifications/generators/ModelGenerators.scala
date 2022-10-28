/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.transitmovementspushnotifications.generators

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import uk.gov.hmrc.transitmovementspushnotifications.models.BoxAssociation
import uk.gov.hmrc.transitmovementspushnotifications.models.BoxId
import uk.gov.hmrc.transitmovementspushnotifications.models.MovementId
import uk.gov.hmrc.transitmovementspushnotifications.models.MovementType
import uk.gov.hmrc.transitmovementspushnotifications.models.request.BoxAssociationRequest
import uk.gov.hmrc.transitmovementspushnotifications.models.responses.BoxResponse

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

trait ModelGenerators extends BaseGenerators {

  implicit lazy val arbitraryMovementId: Arbitrary[MovementId] =
    Arbitrary {
      Gen
        .listOfN(16, Gen.hexChar)
        .map(
          id => MovementId(id.mkString)
        )
    }

  implicit lazy val arbitraryBoxId: Arbitrary[BoxId] = Arbitrary {
    Gen.delay(BoxId(UUID.randomUUID.toString))
  }

  implicit lazy val arbitraryMovementType: Arbitrary[MovementType] =
    Arbitrary(Gen.oneOf(MovementType.values))

  // Restricts the date times to the range of positive long numbers to avoid overflows.
  implicit lazy val arbitraryOffsetDateTime: Arbitrary[OffsetDateTime] =
    Arbitrary {
      for {
        millis <- Gen.chooseNum(0, Long.MaxValue / 1000L)
      } yield OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)
    }

  implicit lazy val arbitraryBoxAssociationRequest: Arbitrary[BoxAssociationRequest] =
    Arbitrary {
      for {
        clientId     <- arbitrary[String]
        movementType <- arbitrary[MovementType]
        boxId        <- arbitrary[Option[BoxId]]
      } yield BoxAssociationRequest(clientId, movementType, boxId)
    }

  implicit lazy val arbitraryBoxResponse: Arbitrary[BoxResponse] =
    Arbitrary {
      for {
        boxId <- arbitrary[BoxId]
      } yield BoxResponse(boxId)
    }

  implicit lazy val arbitraryBoxAssociation: Arbitrary[BoxAssociation] =
    Arbitrary {
      for {
        boxId        <- arbitrary[BoxId]
        movementId   <- arbitrary[MovementId]
        movementType <- arbitrary[MovementType]
        updated      <- arbitrary[OffsetDateTime]
      } yield BoxAssociation(movementId, boxId, movementType, updated)
    }

}
