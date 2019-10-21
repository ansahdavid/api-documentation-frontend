/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.apidocumentation.services

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.apidocumentation.models.{APIDefinition, ExtendedAPIDefinition, ExtendedAPIVersion}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ProxyAwareApiDefinitionService @Inject()(localSvc: LocalApiDefinitionService,
                                               remoteSvc: RemoteApiDefinitionService
                                              )(implicit val ec: ExecutionContext)
                                              extends BaseApiDefinitionService {

  def fetchAllDefinitions(thirdPartyDeveloperEmail: Option[String])
                         (implicit hc: HeaderCarrier): Future[Seq[APIDefinition]] = {

    val localFuture = localSvc.fetchAllDefinitions(thirdPartyDeveloperEmail)
    val remoteFuture = remoteSvc.fetchAllDefinitions(thirdPartyDeveloperEmail)
    mergeSeqsOfDefinitions(remoteFuture,localFuture) map filterDefinitions
  }

  private def mergeSeqsOfDefinitions(remoteFuture: Future[Seq[APIDefinition]], localFuture: Future[Seq[APIDefinition]]) = {
    for {
      remoteDefinitions <- remoteFuture
      localDefinitions <- localFuture
    } yield (remoteDefinitions ++ localDefinitions.filterNot(_.isIn(remoteDefinitions))).sortBy(_.name)
  }

  def filterDefinitions(apis: Seq[APIDefinition]): Seq[APIDefinition] = {
    def apiRequiresTrust(api: APIDefinition): Boolean = {
      api.requiresTrust match {
        case Some(true) => true
        case _ => false
      }
    }

    apis.filter(api => !apiRequiresTrust(api) && api.hasActiveVersions)
  }

  def fetchExtendedDefinition(serviceName: String, thirdPartyDeveloperEmail: Option[String])
                        (implicit hc: HeaderCarrier): Future[Option[ExtendedAPIDefinition]] = {
    val localFuture = localSvc.fetchExtendedDefinition(serviceName, thirdPartyDeveloperEmail)
    val remoteFuture = remoteSvc.fetchExtendedDefinition(serviceName, thirdPartyDeveloperEmail)

    for {
      maybeLocalDefinition <- localFuture
      maybeRemoteDefinition <- remoteFuture
      combined = combine(maybeLocalDefinition, maybeRemoteDefinition)
    } yield combined.filterNot(_.requiresTrust)
  }

  private def combine(maybeLocalDefinition: Option[ExtendedAPIDefinition], maybeRemoteDefinition: Option[ExtendedAPIDefinition]) = {
    def findProductionDefinition(maybeLocalDefinition: Option[ExtendedAPIDefinition], maybeRemoteDefinition: Option[ExtendedAPIDefinition]) = {
      if (maybeLocalDefinition.exists(_.versions.exists(_.productionAvailability.isDefined))) {
        maybeLocalDefinition
      } else {
        maybeRemoteDefinition
      }
    }

    def findSandboxDefinition(maybeLocalDefinition: Option[ExtendedAPIDefinition], maybeRemoteDefinition: Option[ExtendedAPIDefinition]) = {
      if (maybeLocalDefinition.exists(_.versions.exists(_.sandboxAvailability.isDefined))) {
        maybeLocalDefinition
      } else {
        maybeRemoteDefinition
      }
    }

    def combineVersion(maybeProductionVersion: Option[ExtendedAPIVersion], maybeSandboxVersion: Option[ExtendedAPIVersion]) = {
      maybeProductionVersion.fold(maybeSandboxVersion) { productionVersion =>
        maybeSandboxVersion.fold(maybeProductionVersion) { sandboxVersion =>
          Some(sandboxVersion.copy(productionAvailability = productionVersion.productionAvailability))
        }
      }
    }

    def combineVersions(productionVersions: Seq[ExtendedAPIVersion], sandboxVersions: Seq[ExtendedAPIVersion]): Seq[ExtendedAPIVersion] = {
      val allVersions = (productionVersions.map(_.version) ++ sandboxVersions.map(_.version)).distinct.sorted
      allVersions.flatMap { version =>
        combineVersion(productionVersions.find(_.version == version), sandboxVersions.find(_.version == version))
      }
    }

    val maybeProductionDefinition = findProductionDefinition(maybeLocalDefinition, maybeRemoteDefinition)
    val maybeSandboxDefinition = findSandboxDefinition(maybeLocalDefinition, maybeRemoteDefinition)

    maybeProductionDefinition.fold(maybeSandboxDefinition) { productionDefinition =>
      maybeSandboxDefinition.fold(maybeProductionDefinition) { sandboxDefinition =>
        Some(sandboxDefinition.copy(versions = combineVersions(productionDefinition.versions, sandboxDefinition.versions)))
      }
    }
  }
}