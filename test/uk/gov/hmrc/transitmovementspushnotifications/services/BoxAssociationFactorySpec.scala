/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.transitmovementspushnotifications.services

import org.scalacheck.Arbitrary.arbitrary
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.transitmovementspushnotifications.base.SpecBase
import uk.gov.hmrc.transitmovementspushnotifications.generators.ModelGenerators
import uk.gov.hmrc.transitmovementspushnotifications.models.common
import uk.gov.hmrc.transitmovementspushnotifications.models.common.BoxId
import uk.gov.hmrc.transitmovementspushnotifications.models.common.EORINumber
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MovementId
import uk.gov.hmrc.transitmovementspushnotifications.models.common.MovementType

import java.security.SecureRandom
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset

class BoxAssociationFactorySpec extends SpecBase with ModelGenerators with ScalaCheckDrivenPropertyChecks {

  val instant: OffsetDateTime = OffsetDateTime.of(2022, 5, 27, 11, 0, 0, 0, ZoneOffset.UTC)
  val clock: Clock            = Clock.fixed(instant.toInstant, ZoneOffset.UTC)
  val random                  = new SecureRandom

  "create box association" - {
    val sut = new BoxAssociationFactoryImpl(clock)

    "will create a box association that matches the inputs" in forAll(
      arbitrary[BoxId],
      arbitrary[MovementId],
      arbitrary[MovementType],
      arbitrary[EORINumber]
    ) {
      (boxId, movementId, movementType, enrollmentEORINumber) =>
        val boxAssociation = sut.create(boxId, movementId, movementType, enrollmentEORINumber)
        boxAssociation mustBe common.BoxAssociation(
          movementId,
          boxId,
          movementType,
          instant,
          Some(enrollmentEORINumber)
        )
    }
  }
}
