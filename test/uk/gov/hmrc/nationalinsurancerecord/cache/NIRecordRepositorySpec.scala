/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.nationalinsurancerecord.cache

import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.libs.json.Json
import reactivemongo.api.indexes.CollectionIndexesManager
import reactivemongo.json.collection.JSONCollection
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.nationalinsurancerecord.NationalInsuranceRecordUnitSpec
import uk.gov.hmrc.nationalinsurancerecord.domain.APITypes
import uk.gov.hmrc.nationalinsurancerecord.domain.nps.NpsNIRecord
import uk.gov.hmrc.nationalinsurancerecord.services.{CachingMongoService, MetricsService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NIRecordRepositorySpec extends NationalInsuranceRecordUnitSpec with OneServerPerSuite with MongoSpecSupport with MockitoSugar {

  val niRecordJson = Json.parse(
    """
      | {
      | "years_to_fry": 1,
      | "non_qualifying_years": 13,
      | "date_of_entry": "1973-10-01",
      | "npsLniemply": [],
      | "pre_75_cc_count": 51,
      | "number_of_qualifying_years": 27,
      | "npsErrlist": {
      |   "count": 0,
      | "mgt_check": 0,
      | "commit_status": 2,
      | "npsErritem": [],
      | "bfm_return_code": 0,
      | "data_not_found": 0
      |},
      |"non_qualifying_years_payable": 0,
      |  "npsLnitaxyr": [
      | {
      | "class_three_payable_by_penalty": null,
      | "class_two_outstanding_weeks": null,
      | "class_two_payable": null,
      | "qualifying": 1,
      | "under_investigation_flag": 0,
      | "class_two_payable_by": null,
      | "co_class_one_paid": null,
      | "class_two_payable_by_penalty": null,
      | "co_primary_paid_earnings": null,
      | "payable": 0,
      | "rattd_tax_year": 2012,
      | "ni_earnings": null,
      | "amount_needed": null,
      | "primary_paid_earnings": "21750.0000",
      | "class_three_payable": null,
      | "ni_earnings_employed": "1698.9600",
      | "npsLothcred": [
      |   {
      |      "credit_source_type": 2,
      |      "cc_type": 23,
      |      "no_of_credits_and_conts": 4
      |   }
      | ],
      | "ni_earnings_self_employed": null,
      | "class_three_payable_by": null,
      | "ni_earnings_voluntary": null
      |},
      |{
      | "class_three_payable_by_penalty": "2023-04-05",
      | "class_two_outstanding_weeks": null,
      | "class_two_payable": null,
      | "qualifying": 0,
      | "under_investigation_flag": 1,
      | "class_two_payable_by": null,
      | "co_class_one_paid": null,
      | "class_two_payable_by_penalty": null,
      | "co_primary_paid_earnings": null,
      | "payable": 1,
      | "rattd_tax_year": 2013,
      | "ni_earnings": null,
      | "amount_needed": null,
      | "primary_paid_earnings": null,
      | "class_three_payable": 722.80,
      | "ni_earnings_employed": null,
      | "npsLothcred": [],
      | "ni_earnings_self_employed": "52",
      | "class_three_payable_by": "2019-04-05",
      | "ni_earnings_voluntary": null
      |}
      |],
      | "nino": "<NINO>"
      |}
    """.stripMargin)

  val niRecord = niRecordJson.as[NpsNIRecord]

  val nino = generateNino()
  val excludedNino = generateNino()

  "NationalInsuranceRepository" should {

    val service = new CachingMongoService[NIRecordCache, NpsNIRecord](NIRecordCache.formats, NIRecordCache.apply,
      APITypes.NIRecord, StubApplicationConfig, mock[MetricsService])

    "persist a NIRecord in the repo" in {
      val resultF = service.insertByNino(nino, niRecord)
      await(resultF) shouldBe true
    }

    "find a NIRecord in the repo" in {
      val resultF = service.findByNino(nino)
      resultF.get shouldBe niRecord
    }

    "return None when there is nothing in the repo" in {
      val resultF = service.findByNino(excludedNino)
      await(resultF) shouldBe None
    }

    "return None when there is a Mongo error" in {

      val stubCollection = mock[JSONCollection]
      val stubIndexesManager = mock[CollectionIndexesManager]

      when(stubCollection.indexesManager).thenReturn(stubIndexesManager)

      class TestSummaryMongoService extends CachingMongoService[NIRecordCache, NpsNIRecord
        ](NIRecordCache.formats, NIRecordCache.apply, APITypes.NIRecord, StubApplicationConfig, mock[MetricsService])  {
        override lazy val collection = stubCollection
      }
      when(stubCollection.find(Matchers.any())(Matchers.any())).thenThrow(new RuntimeException)
      when(stubCollection.indexesManager.ensure(Matchers.any())).thenReturn(Future.successful(true))

      val testRepository = new TestSummaryMongoService

      val found = await(testRepository.findByNino(excludedNino))
      found shouldBe None
    }

    "multiple calls to insertByNino should be fine (upsert)" in {
      await(service.insertByNino(nino, niRecord)) shouldBe true
      await(service.insertByNino(nino, niRecord)) shouldBe true
    }
  }



}
