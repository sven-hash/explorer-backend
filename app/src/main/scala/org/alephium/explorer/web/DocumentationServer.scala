// Copyright 2018 The Alephium Authors
// This file is part of the alephium project.
//
// The library is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// The library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the library. If not, see <http://www.gnu.org/licenses/>.

package org.alephium.explorer.web

import scala.concurrent.{ExecutionContext, Future}

import akka.http.scaladsl.server.Route
import sttp.tapir.swagger.{SwaggerUI, SwaggerUIOptions}

import org.alephium.api.OpenAPIWriters.openApiJson
import org.alephium.explorer.GroupSetting
import org.alephium.explorer.docs.Documentation

class DocumentationServer()(implicit val executionContext: ExecutionContext,
                            groupSetting: GroupSetting)
    extends Server
    with Documentation {

  val groupNum = groupSetting.groupNum

  val route: Route = toRoute(
    SwaggerUI[Future](openApiJson(docs, dropAuth             = false),
                      SwaggerUIOptions.default.copy(yamlName = "explorer-backend-openapi.json")))
}
