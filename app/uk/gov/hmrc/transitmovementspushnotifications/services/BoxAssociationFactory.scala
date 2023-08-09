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

import com.google.inject.ImplementedBy
import uk.gov.hmrc.transitmovementspushnotifications.models.BoxAssociation
import uk.gov.hmrc.transitmovementspushnotifications.models.BoxId
import uk.gov.hmrc.transitmovementspushnotifications.models.EORINumber
import uk.gov.hmrc.transitmovementspushnotifications.models.MovementId
import uk.gov.hmrc.transitmovementspushnotifications.models.MovementType

import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.inject.Inject

@ImplementedBy(classOf[BoxAssociationFactoryImpl])
trait BoxAssociationFactory {
  def create(boxId: BoxId, movementId: MovementId, movementType: MovementType, enrollmentEORINumber: EORINumber): BoxAssociation
}

class BoxAssociationFactoryImpl @Inject() (
  clock: Clock
) extends BoxAssociationFactory {

  def create(
    boxId: BoxId,
    movementId: MovementId,
    movementType: MovementType,
    enrollmentEORINumber: EORINumber
  ): BoxAssociation =
    BoxAssociation(
      _id = movementId,
      boxId = boxId,
      movementType = movementType,
      updated = OffsetDateTime.ofInstant(clock.instant, ZoneOffset.UTC),
      enrollmentEORINumber = Some(enrollmentEORINumber)
    )

}
