package com.ganim.nonogram.monetize

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The products from build plan 8.3. */
object Sku {
    /** Non-consumable. Removes all interstitials and banners. ~$2.99. */
    const val REMOVE_ADS = "remove_ads"

    /** Consumable. 25 hints. ~$0.99. */
    const val HINT_PACK_25 = "hint_pack_25"

    val all = listOf(REMOVE_ADS, HINT_PACK_25)
}

/** What the player currently owns. */
data class Entitlements(
    val adFree: Boolean = false,
    /**
     * Products with a purchase Google has accepted but not yet completed - common with
     * the cash and carrier-billing methods used across the target markets. Not an
     * entitlement yet, but the UI should say something rather than looking like the
     * payment failed.
     *
     * Per product rather than one flag, so the row for the product actually waiting can
     * say so, and the other one stays buyable.
     */
    val pendingProducts: Set<String> = emptySet(),
) {
    val hasPendingPurchase: Boolean get() = pendingProducts.isNotEmpty()
}

/** The localized price Play shows for [sku], or null until its details have loaded. */
fun Map<String, ProductDetails>.formattedPrice(sku: String): String? =
    get(sku)?.oneTimePurchaseOfferDetails?.formattedPrice

/**
 * In-app purchases (build plan 8.3).
 *
 * The plan's four requirements and where each is handled:
 *
 *  - *"Query and restore purchases on every app launch"* - [refresh], called from
 *    [connect] and again whenever the app returns to the foreground. Entitlement then
 *    survives a reinstall or a new device without anything being stored locally.
 *  - *"Acknowledge purchases within Play's required window or they are automatically
 *    refunded"* - [handlePurchase] acknowledges immediately on seeing a purchased,
 *    unacknowledged item, including ones found by a restore rather than a fresh buy.
 *  - *"Handle pending purchases"* - surfaced as [Entitlements.hasPendingPurchase] and
 *    deliberately not granted. They complete asynchronously and arrive on a later
 *    refresh.
 *  - *"Verify entitlement locally against the Billing client. No server, so no
 *    server-side receipt validation; accept that tradeoff explicitly for v1."* - the
 *    client's own purchase list is the source of truth. This is forgeable on a rooted
 *    device. For a puzzle app with a $2.99 IAP that is the right trade; it would not be
 *    for anything with real money attached.
 *
 * ## Not yet verified
 *
 * None of this has run against Play's test tracks. The plan's acceptance criterion -
 * purchase, restore-on-reinstall and pending-purchase flows all verified - needs a Play
 * Console account and a real device.
 */
interface BillingManager {
    val entitlements: StateFlow<Entitlements>
    val products: StateFlow<Map<String, ProductDetails>>

    fun connect()
    fun refresh()
    fun launchPurchase(activity: Activity, sku: String)
    fun dispose()
}

class PlayBillingManager(
    context: Context,
    private val scope: CoroutineScope,
    /** Called when a consumable is bought, so hints can be added. */
    private val onHintPackPurchased: (Int) -> Unit = {},
) : BillingManager {

    private val _entitlements = MutableStateFlow(Entitlements())
    override val entitlements: StateFlow<Entitlements> = _entitlements.asStateFlow()

    private val _products = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    override val products: StateFlow<Map<String, ProductDetails>> = _products.asStateFlow()

    private val client = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                scope.launch { purchases.forEach { handlePurchase(it) } }
            }
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    override fun connect() {
        if (client.isReady) {
            refresh()
            // The first load can fail on a bad connection while setup succeeded. Without
            // a retry the store would stay unpriced, and so unbuyable, until a relaunch.
            if (_products.value.isEmpty()) scope.launch { loadProducts() }
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refresh()
                    scope.launch { loadProducts() }
                }
            }

            override fun onBillingServiceDisconnected() {
                // Reconnect lazily on the next call rather than looping here; a retry
                // storm on a flaky connection is worse than a delayed entitlement.
            }
        })
    }

    /** Build plan 8.3: query and restore on every launch. */
    override fun refresh() {
        if (!client.isReady) return
        scope.launch {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            val result = client.queryPurchasesAsync(params)
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return@launch

            var adFree = false
            val pending = mutableSetOf<String>()
            result.purchasesList.forEach { purchase ->
                when (purchase.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> {
                        if (Sku.REMOVE_ADS in purchase.products) adFree = true
                        handlePurchase(purchase, grantConsumables = false)
                    }
                    Purchase.PurchaseState.PENDING -> pending += purchase.products
                }
            }
            _entitlements.value = Entitlements(adFree = adFree, pendingProducts = pending)
        }
    }

    private suspend fun loadProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                Sku.all.map { sku ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(sku)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                },
            )
            .build()
        val result = client.queryProductDetails(params)
        _products.value = result.productDetailsList.orEmpty().associateBy { it.productId }
    }

    /**
     * Acknowledges, consumes and grants.
     *
     * Acknowledgement is not optional: Play automatically refunds a purchase that is not
     * acknowledged inside its window, so a player would be charged, get the feature, and
     * then silently lose both.
     */
    private suspend fun handlePurchase(purchase: Purchase, grantConsumables: Boolean = true) {
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            _entitlements.value = _entitlements.value.let {
                it.copy(pendingProducts = it.pendingProducts + purchase.products)
            }
            return
        }
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // A pending purchase that completes while the app is open arrives here through
        // the listener, not through a refresh, so this is where it stops being pending.
        _entitlements.value = _entitlements.value.let {
            it.copy(pendingProducts = it.pendingProducts - purchase.products.toSet())
        }

        when {
            Sku.HINT_PACK_25 in purchase.products -> {
                // Consumables are consumed rather than acknowledged; consuming counts as
                // acknowledgement, and it is what makes the pack buyable again.
                val result = client.consumePurchase(
                    ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
                )
                if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK &&
                    grantConsumables
                ) {
                    onHintPackPurchased(HintEconomy.HINT_PACK_SIZE)
                }
            }

            else -> {
                if (!purchase.isAcknowledged) {
                    client.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build(),
                    )
                }
                if (Sku.REMOVE_ADS in purchase.products) {
                    _entitlements.value = _entitlements.value.copy(adFree = true)
                }
            }
        }
    }

    override fun launchPurchase(activity: Activity, sku: String) {
        val details = _products.value[sku] ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    override fun dispose() {
        if (client.isReady) client.endConnection()
    }
}
