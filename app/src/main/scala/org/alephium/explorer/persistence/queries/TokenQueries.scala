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

package org.alephium.explorer.persistence.queries

import scala.concurrent.ExecutionContext

import com.typesafe.scalalogging.StrictLogging
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.queries.result.TxByAddressQR
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.util.{TimeStamp, U256}

object TokenQueries extends StrictLogging {

  def getTokenBalanceAction(address: Address, token: Hash)(
      implicit ec: ExecutionContext): DBActionR[(U256, U256)] =
    getTokenBalanceUntilLockTime(
      address = address,
      token,
      lockTime = TimeStamp.now()
    ) map {
      case (total, locked) =>
        (total.getOrElse(U256.Zero), locked.getOrElse(U256.Zero))
    }

  def getTokenBalanceUntilLockTime(address: Address, token: Hash, lockTime: TimeStamp)(
      implicit ec: ExecutionContext): DBActionR[(Option[U256], Option[U256])] =
    sql"""
      SELECT sum(token_outputs.amount),
             sum(CASE
                     WHEN token_outputs.lock_time is NULL or token_outputs.lock_time < ${lockTime.millis} THEN 0
                     ELSE token_outputs.amount
                 END)
      FROM token_outputs
               LEFT JOIN inputs
                         ON token_outputs.key = inputs.output_ref_key
                             AND inputs.main_chain = true
      WHERE token_outputs.spent_finalized IS NULL
        AND token_outputs.address = $address
        AND token_outputs.token = $token
        AND token_outputs.main_chain = true
        AND inputs.block_hash IS NULL;
    """.as[(Option[U256], Option[U256])].exactlyOne

  def listTokensAction(pagination: Pagination): DBActionSR[Hash] = {
    val offset = pagination.offset
    val limit  = pagination.limit
    val toDrop = offset * limit
    sql"""
      SELECT token
      FROM token_info
      ORDER BY last_used DESC
      LIMIT $limit
      OFFSET $toDrop
    """.as[Hash]
  }

  def getTransactionsByToken(token: Hash, pagination: Pagination)(
      implicit ec: ExecutionContext): DBActionR[Seq[Transaction]] = {
    val offset = pagination.offset
    val limit  = pagination.limit
    val toDrop = offset * limit
    for {
      txHashesTs <- listTokenTransactionsAction(token, toDrop, limit)
      txs        <- TransactionQueries.getTransactionsSQL(txHashesTs)
    } yield txs
  }

  def getAddressesByToken(token: Hash, pagination: Pagination): DBActionR[Seq[Address]] = {
    val offset = pagination.offset
    val limit  = pagination.limit
    val toDrop = offset * limit
    sql"""
      SELECT DISTINCT address
      FROM token_tx_per_addresses
      WHERE token = $token
      LIMIT $limit
      OFFSET $toDrop
    """.as[Address]
  }

  def listTokenTransactionsAction(token: Hash,
                                  offset: Int,
                                  limit: Int): DBActionSR[TxByAddressQR] = {
    sql"""
      SELECT tx_hash, block_hash, block_timestamp, tx_order
      FROM transaction_per_token
      WHERE main_chain = true
      AND token = $token
      ORDER BY block_timestamp DESC, tx_order
      LIMIT $limit
      OFFSET $offset
    """.as[TxByAddressQR]
  }

  def listAddressTokensAction(address: Address): DBActionSR[Hash] =
    sql"""
      SELECT DISTINCT token
      FROM token_tx_per_addresses
      WHERE address = $address
      AND main_chain = true
    """.as[Hash]

  def getTokenTransactionsByAddress(address: Address, token: Hash, pagination: Pagination)(
      implicit ec: ExecutionContext): DBActionR[Seq[Transaction]] = {
    val offset = pagination.offset
    val limit  = pagination.limit
    val toDrop = offset * limit
    for {
      txHashesTs <- getTokenTxHashesByAddressQuery(address, token, toDrop, limit)
      txs        <- TransactionQueries.getTransactionsSQL(txHashesTs)
    } yield txs
  }

  def getTokenTxHashesByAddressQuery(address: Address,
                                     token: Hash,
                                     offset: Int,
                                     limit: Int): DBActionSR[TxByAddressQR] = {
    sql"""
      SELECT tx_hash, block_hash, block_timestamp, tx_order
      FROM token_tx_per_addresses
      WHERE main_chain = true
      AND address = $address
      AND token = $token
      ORDER BY block_timestamp DESC, tx_order
      LIMIT $limit
      OFFSET $offset
    """.as
  }
}
