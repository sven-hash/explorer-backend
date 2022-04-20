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

package org.alephium.explorer.persistence.schema

import java.math.BigInteger

import akka.util.ByteString
import slick.jdbc.{GetResult, PositionedResult}

import org.alephium.explorer.{BlockHash, Hash}
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model.{AppState, BlockHeader}
import org.alephium.util.{TimeStamp, U256}

object CustomGetResult {

  /**
    * GetResult types
    */
  implicit val blockEntryHashGetResult: GetResult[BlockEntry.Hash] =
    (result: PositionedResult) =>
      new BlockEntry.Hash(new BlockHash(ByteString.fromArrayUnsafe(result.nextBytes())))

  implicit val txHashGetResult: GetResult[Transaction.Hash] =
    (result: PositionedResult) =>
      new Transaction.Hash(new Hash(ByteString.fromArrayUnsafe(result.nextBytes())))

  implicit val optionTxHashGetResult: GetResult[Option[Transaction.Hash]] =
    (result: PositionedResult) =>
      result
        .nextBytesOption()
        .map(bytes => new Transaction.Hash(new Hash(ByteString.fromArrayUnsafe(bytes))))

  implicit val optionBlockEntryHashGetResult: GetResult[Option[BlockEntry.Hash]] =
    (result: PositionedResult) =>
      result
        .nextBytesOption()
        .map(bytes => new BlockEntry.Hash(new BlockHash(ByteString.fromArrayUnsafe(bytes))))

  implicit val timestampGetResult: GetResult[TimeStamp] =
    (result: PositionedResult) => TimeStamp.unsafe(result.nextLong())

  implicit val optionTimestampGetResult: GetResult[Option[TimeStamp]] =
    (result: PositionedResult) => result.nextLongOption().map(TimeStamp.unsafe)

  implicit val groupIndexGetResult: GetResult[GroupIndex] =
    (result: PositionedResult) => GroupIndex.unsafe(result.nextInt())

  implicit val heightGetResult: GetResult[Height] =
    (result: PositionedResult) => Height.unsafe(result.nextInt())

  implicit val bigIntegerGetResult: GetResult[BigInteger] =
    (result: PositionedResult) => result.nextBigDecimal().toBigInt.bigInteger

  implicit val byteStringGetResult: GetResult[ByteString] =
    (result: PositionedResult) => ByteString.fromArrayUnsafe(result.nextBytes())

  implicit val hashGetResult: GetResult[Hash] =
    (result: PositionedResult) => Hash.unsafe(ByteString.fromArrayUnsafe(result.nextBytes()))

  implicit val addressGetResult: GetResult[Address] =
    (result: PositionedResult) => Address.unsafe(result.nextString())

  implicit val u256GetResult: GetResult[U256] =
    (result: PositionedResult) => {
      U256.unsafe(result.nextBigDecimal().toBigInt.bigInteger)
    }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  implicit lazy val appStateValueGetResult: GetResult[AppState.Value] =
    (result: PositionedResult) =>
      AppState.Value.deserialize(ByteString.fromArrayUnsafe(result.nextBytes())).toOption.get

  @SuppressWarnings(
    Array("org.wartremover.warts.OptionPartial", "org.wartremover.warts.AsInstanceOf"))
  implicit lazy val appStateTimeGetResult: GetResult[AppState.Time] =
    (result: PositionedResult) =>
      AppState.Value
        .deserialize(ByteString.fromArrayUnsafe(result.nextBytes()))
        .toOption
        .get
        .asInstanceOf[AppState.Time]

  implicit val optionU256GetResult: GetResult[Option[U256]] =
    (result: PositionedResult) => {
      result.nextBigDecimalOption().map(bigDecimal => U256.unsafe(bigDecimal.toBigInt.bigInteger))
    }

  /**
    * GetResult type for BlockEntryLite
    *
    * @note The order in which the query returns the column values matters.
    *       For example: Getting (`.<<`) `chainTo` before `chainFrom` when
    *       `chainFrom` is before `chainTo` in the query result would compile
    *       but would result in incorrect data.
    */
  val blockEntryListGetResult: GetResult[BlockEntryLite] =
    (result: PositionedResult) =>
      BlockEntryLite(hash      = result.<<,
                     timestamp = result.<<,
                     chainFrom = result.<<,
                     chainTo   = result.<<,
                     height    = result.<<,
                     mainChain = result.<<,
                     hashRate  = result.<<,
                     txNumber  = result.<<)

  val blockHeaderGetResult: GetResult[BlockHeader] =
    (result: PositionedResult) =>
      BlockHeader(
        hash         = result.<<,
        timestamp    = result.<<,
        chainFrom    = result.<<,
        chainTo      = result.<<,
        height       = result.<<,
        mainChain    = result.<<,
        nonce        = result.<<,
        version      = result.<<,
        depStateHash = result.<<,
        txsHash      = result.<<,
        txsCount     = result.<<,
        target       = result.<<,
        hashrate     = result.<<,
        parent       = result.<<?
    )

}
