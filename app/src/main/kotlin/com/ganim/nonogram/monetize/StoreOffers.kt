package com.ganim.nonogram.monetize

/** What a purchase button can honestly offer right now. */
sealed interface Offer {
    /**
     * No price from Play yet - still connecting, offline, or no Play Store at all.
     * Nothing to buy: launching a purchase without product details does nothing, and a
     * button that does nothing reads as broken.
     */
    data object Unavailable : Offer

    data class ForSale(val price: String) : Offer

    /** Paid with a method that settles later. Says so, so the payment does not look lost. */
    data object Pending : Offer

    /** Non-consumables only. A consumable is never owned; it is used up. */
    data object Owned : Offer

    val canBuy: Boolean get() = this is ForSale
}

/**
 * Turns entitlements and a Play price into the [Offer] a screen shows.
 *
 * Pure, so the order of precedence is tested rather than rediscovered per screen.
 */
object StoreOffers {

    /**
     * Owned beats pending beats a price.
     *
     * [adFree] is passed separately from [entitlements] so callers can fold in the cached
     * answer: billing reports "not owned" until it connects, and someone who has paid must
     * not be offered it again in the meantime.
     */
    fun removeAds(entitlements: Entitlements, price: String?, adFree: Boolean = entitlements.adFree): Offer =
        when {
            adFree -> Offer.Owned
            Sku.REMOVE_ADS in entitlements.pendingProducts -> Offer.Pending
            else -> priced(price)
        }

    fun hintPack(entitlements: Entitlements, price: String?): Offer =
        when {
            Sku.HINT_PACK_25 in entitlements.pendingProducts -> Offer.Pending
            else -> priced(price)
        }

    private fun priced(price: String?): Offer =
        if (price.isNullOrBlank()) Offer.Unavailable else Offer.ForSale(price)
}
