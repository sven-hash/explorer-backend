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

package org.alephium.explorer.api

import sttp.tapir._
import sttp.tapir.generic.auto._

import org.alephium.api.{alphJsonBody => jsonBody}
import org.alephium.explorer.Hash
import org.alephium.explorer.api.BaseEndpoint
import org.alephium.explorer.api.Codecs.transactionHashTapirCodec
import org.alephium.explorer.api.model.{ConfirmedTransaction, Transaction, TransactionLike}

trait TransactionEndpoints extends BaseEndpoint {

  private val transactionsEndpoint =
    baseEndpoint
      .tag("Transactions")
      .in("transactions")

  val getTransactionById: BaseEndpoint[Transaction.Hash, TransactionLike] =
    transactionsEndpoint.get
      .in(path[Transaction.Hash]("transaction-hash"))
      .out(jsonBody[TransactionLike])
      .description("Get a transaction with hash")

  val getOutputRefTransaction: BaseEndpoint[Hash, ConfirmedTransaction] =
    baseEndpoint
      .tag("Transactions")
      .in("transaction-by-output-ref-key")
      .get
      .in(path[Hash]("output-ref-key"))
      .out(jsonBody[ConfirmedTransaction])
      .description("Get a transaction from a output reference key")
}
