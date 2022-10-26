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

package uk.gov.hmrc.transitmovementspushnotifications.services

import uk.gov.hmrc.transitmovementspushnotifications.base.SpecBase
import uk.gov.hmrc.transitmovementspushnotifications.generators.ModelGenerators

import java.security.SecureRandom
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset

class BoxAssociationFactorySpec extends SpecBase with ModelGenerators {

  val instant: OffsetDateTime = OffsetDateTime.of(2022, 5, 27, 11, 0, 0, 0, ZoneOffset.UTC)
  val clock: Clock            = Clock.fixed(instant.toInstant, ZoneOffset.UTC)
  val random                  = new SecureRandom

  "create" - {
    val sut = new BoxAssociationFactoryImpl(clock)

    "will create a box association with _id equal to the movementId supplied" in {
      val boxId      = arbitraryBoxId.arbitrary.sample.get
      val movementId = arbitraryMovementId.arbitrary.sample.get

      val boxAssociation = sut.create(boxId, movementId, "arrivals")

      boxAssociation._id.value mustBe movementId.value
    }
  }

}
