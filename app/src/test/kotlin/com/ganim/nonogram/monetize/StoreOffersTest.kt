package com.ganim.nonogram.monetize

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class StoreOffersTest {

    @Nested
    inner class RemoveAds {

        @Test
        fun `priced once product details load`() {
            val offer = StoreOffers.removeAds(Entitlements(), price = "$2.99")
            offer shouldBe Offer.ForSale("$2.99")
            offer.canBuy shouldBe true
        }

        @Test
        fun `not buyable before product details load`() {
            StoreOffers.removeAds(Entitlements(), price = null) shouldBe Offer.Unavailable
            StoreOffers.removeAds(Entitlements(), price = "") shouldBe Offer.Unavailable
            Offer.Unavailable.canBuy shouldBe false
        }

        @Test
        fun `owned wins over a price`() {
            StoreOffers.removeAds(Entitlements(adFree = true), price = "$2.99") shouldBe Offer.Owned
        }

        @Test
        fun `the cached answer counts as owned before billing connects`() {
            StoreOffers.removeAds(Entitlements(), price = null, adFree = true) shouldBe Offer.Owned
        }

        @Test
        fun `pending shows even without a price, and cannot be bought twice`() {
            val pending = Entitlements(pendingProducts = setOf(Sku.REMOVE_ADS))
            StoreOffers.removeAds(pending, price = "$2.99") shouldBe Offer.Pending
            StoreOffers.removeAds(pending, price = null) shouldBe Offer.Pending
            Offer.Pending.canBuy shouldBe false
        }

        @Test
        fun `a pending hint pack does not block remove ads`() {
            val pending = Entitlements(pendingProducts = setOf(Sku.HINT_PACK_25))
            StoreOffers.removeAds(pending, price = "$2.99") shouldBe Offer.ForSale("$2.99")
        }
    }

    @Nested
    inner class HintPack {

        @Test
        fun `priced once product details load`() {
            StoreOffers.hintPack(Entitlements(), price = "$0.99") shouldBe Offer.ForSale("$0.99")
            StoreOffers.hintPack(Entitlements(), price = null) shouldBe Offer.Unavailable
        }

        @Test
        fun `never owned, even for an ad-free player`() {
            StoreOffers.hintPack(Entitlements(adFree = true), price = "$0.99") shouldBe
                Offer.ForSale("$0.99")
        }

        @Test
        fun `pending only when the pack itself is pending`() {
            StoreOffers.hintPack(Entitlements(pendingProducts = setOf(Sku.HINT_PACK_25)), "$0.99") shouldBe
                Offer.Pending
            StoreOffers.hintPack(Entitlements(pendingProducts = setOf(Sku.REMOVE_ADS)), "$0.99") shouldBe
                Offer.ForSale("$0.99")
        }
    }

    @Test
    fun `hasPendingPurchase follows the pending set`() {
        Entitlements().hasPendingPurchase shouldBe false
        Entitlements(pendingProducts = setOf(Sku.HINT_PACK_25)).hasPendingPurchase shouldBe true
    }
}
