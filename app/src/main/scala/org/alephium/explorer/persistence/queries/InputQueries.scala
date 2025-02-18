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

import slick.dbio.DBIOAction
import slick.jdbc.{PositionedParameters, SetParameter, SQLActionBuilder}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.result.{InputsFromTxQR, InputsQR}
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.persistence.schema.InputSchema
import org.alephium.explorer.util.SlickExplainUtil._
import org.alephium.explorer.util.SlickUtil._

object InputQueries {

  /** Inserts inputs or ignore rows with primary key conflict */
  // scalastyle:off magic.number
  def insertInputs(inputs: Iterable[InputEntity]): DBActionW[Int] =
    QuerySplitter.splitUpdates(rows = inputs, columnsPerRow = 11) { (inputs, placeholder) =>
      val query =
        s"""
           |INSERT INTO inputs ("block_hash",
           |                    "tx_hash",
           |                    "block_timestamp",
           |                    "hint",
           |                    "output_ref_key",
           |                    "unlock_script",
           |                    "main_chain",
           |                    "input_order",
           |                    "tx_order",
           |                    "output_ref_address",
           |                    "output_ref_amount")
           |VALUES $placeholder
           |ON CONFLICT
           |    ON CONSTRAINT inputs_pk
           |    DO NOTHING
           |""".stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          inputs foreach { input =>
            params >> input.blockHash
            params >> input.txHash
            params >> input.timestamp
            params >> input.hint
            params >> input.outputRefKey
            params >> input.unlockScript
            params >> input.mainChain
            params >> input.inputOrder
            params >> input.txOrder
            params >> input.outputRefAddress
            params >> input.outputRefAmount
        }

      SQLActionBuilder(
        queryParts = query,
        unitPConv  = parameters
      ).asUpdate
    }

  def inputsFromTxsSQL(txHashes: Seq[Transaction.Hash]): DBActionR[Seq[InputsFromTxQR]] =
    if (txHashes.nonEmpty) {
      val values = txHashes.map(hash => s"'\\x$hash'").mkString(",")
      sql"""
          SELECT inputs.tx_hash,
                 inputs.input_order,
                 inputs.hint,
                 inputs.output_ref_key,
                 inputs.unlock_script,
                 outputs.address,
                 outputs.amount,
                 outputs.tokens
          FROM inputs
                   JOIN outputs
                        ON inputs.output_ref_key = outputs.KEY
                            AND outputs.main_chain = true
          WHERE inputs.tx_hash IN (#$values)
            AND inputs.main_chain = true
    """.as[InputsFromTxQR]
    } else {
      DBIOAction.successful(Seq.empty)
    }

  def inputsFromTxsNoJoin(
      hashes: Seq[(Transaction.Hash, BlockEntry.Hash)]): DBActionR[Seq[InputsFromTxQR]] =
    if (hashes.nonEmpty) {
      inputsFromTxsNoJoinSQLBuilder(hashes).as[InputsFromTxQR]
    } else {
      DBIOAction.successful(Seq.empty)
    }

  private def inputsFromTxsNoJoinSQLBuilder(
      hashes: Seq[(Transaction.Hash, BlockEntry.Hash)]): SQLActionBuilder = {
    val params = paramPlaceholderTuple2(1, hashes.size)

    val query =
      s"""
           |SELECT tx_hash,
           |       input_order,
           |       hint,
           |       output_ref_key,
           |       unlock_script,
           |       output_ref_address,
           |       output_ref_amount,
           |       output_ref_tokens
           |FROM inputs
           |WHERE (tx_hash, block_hash) IN $params
           |
           |""".stripMargin

    val parameters: SetParameter[Unit] =
      (_: Unit, params: PositionedParameters) =>
        hashes foreach {
          case (txnHash, blockHash) =>
            params >> txnHash
            params >> blockHash
      }

    SQLActionBuilder(
      queryParts = query,
      unitPConv  = parameters
    )
  }

  def getInputsQuery(txHash: Transaction.Hash, blockHash: BlockEntry.Hash): DBActionSR[InputsQR] =
    sql"""
        SELECT hint,
               output_ref_key,
               unlock_script,
               output_ref_address,
               output_ref_amount,
               output_ref_tokens
        FROM inputs
        WHERE tx_hash = $txHash
          AND block_hash = $blockHash
        ORDER BY input_order
    """.as[InputsQR]

  def getMainChainInputs(ascendingOrder: Boolean): Query[InputSchema.Inputs, InputEntity, Seq] = {
    val mainChain = InputSchema.table.filter(_.mainChain === true)

    if (ascendingOrder) {
      mainChain.sortBy(_.timestamp.asc)
    } else {
      mainChain.sortBy(_.timestamp.desc)
    }
  }

  /** Runs explain on query `inputsFromTxsNoJoin` and checks the index `inputs_tx_hash_block_hash_idx`
    * is being used */
  def explainInputsFromTxsNoJoin(hashes: Seq[(Transaction.Hash, BlockEntry.Hash)])(
      implicit ec: ExecutionContext): DBActionR[ExplainResult] = {
    val queryName = "inputsFromTxsNoJoin"
    if (hashes.isEmpty) {
      DBIOAction.successful(ExplainResult.emptyInput(queryName))
    } else {
      inputsFromTxsNoJoinSQLBuilder(hashes).explainAnalyze() map { explain =>
        ExplainResult(
          queryName  = queryName,
          queryInput = hashes.toString(),
          explain    = explain,
          messages   = Iterable.empty,
          passed     = explain.exists(_.contains("inputs_tx_hash_block_hash_idx"))
        )
      }
    }
  }
}
