package uk.gov.hmrc.transitmovementspushnotifications.base

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.BeforeAndAfterEach
import org.scalatest.EitherValues
import org.scalatest.OptionValues
import org.scalatestplus.mockito.MockitoSugar

trait SpecBase extends AnyFreeSpec with Matchers with MockitoSugar with ScalaFutures with OptionValues with EitherValues with BeforeAndAfterEach {}
