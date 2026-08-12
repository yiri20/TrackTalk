package com.trackvoice.monetization

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PremiumState(
    val isPremium: Boolean = false,
    val billingReady: Boolean = false,
    val productAvailable: Boolean = false,
    val price: String? = null,
    val isLoading: Boolean = false,
    val message: PremiumMessage? = null,
    val isLocalUnlock: Boolean = false,
)

enum class PremiumMessage {
    BILLING_UNAVAILABLE,
    SERVICE_DISCONNECTED,
    PRODUCT_UNAVAILABLE,
    PRODUCT_LOAD_FAILED,
    PURCHASE_UNAVAILABLE,
    PURCHASE_FLOW_FAILED,
    PURCHASE_CANCELED,
    PURCHASE_FAILED,
    RESTORE_FAILED,
    PENDING,
    LOCAL_CODE_APPLIED,
    ACKNOWLEDGE_FAILED,
}

class PlayBillingManager(context: Context) : PurchasesUpdatedListener {
    companion object {
        // Create this as a one-time in-app product in Play Console before release.
        const val PREMIUM_PRODUCT_ID = "tracktalk_plus_lifetime"
    }

    private val appContext = context.applicationContext
    private val localEntitlementPreferences = appContext.getSharedPreferences(
        "tracktalk_entitlements",
        Context.MODE_PRIVATE,
    )
    private var localPlusUnlocked = localEntitlementPreferences.getBoolean("local_plus_unlocked", false)
    private var billingPlusPurchased = false
    private val _state = MutableStateFlow(
        PremiumState(
            isPremium = localPlusUnlocked,
            isLocalUnlock = localPlusUnlocked,
        ),
    )
    val state: StateFlow<PremiumState> = _state.asStateFlow()

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    private var connecting = false
    private var productDetails: ProductDetails? = null

    fun connect() {
        if (billingClient.isReady || connecting) return
        connecting = true
        _state.value = _state.value.copy(isLoading = true)
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                connecting = false
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = _state.value.copy(
                        billingReady = true,
                        isLoading = false,
                        message = null,
                    )
                    queryProduct()
                    queryPurchases()
                } else {
                    _state.value = _state.value.copy(
                        billingReady = false,
                        isLoading = false,
                        message = PremiumMessage.BILLING_UNAVAILABLE,
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
                _state.value = _state.value.copy(
                    billingReady = false,
                    isLoading = false,
                    message = PremiumMessage.SERVICE_DISCONNECTED,
                )
            }
        })
    }

    fun refresh() {
        if (!billingClient.isReady) {
            connect()
            return
        }
        queryProduct()
        queryPurchases()
    }

    fun launchPurchase(activity: Activity) {
        val details = productDetails
        if (!billingClient.isReady || details == null) {
            _state.value = _state.value.copy(
                message = PremiumMessage.PURCHASE_UNAVAILABLE,
            )
            refresh()
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build(),
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.value = _state.value.copy(
                message = PremiumMessage.PURCHASE_FLOW_FAILED,
            )
        }
    }

    fun restorePurchases() {
        if (!billingClient.isReady) {
            connect()
            return
        }
        _state.value = _state.value.copy(isLoading = true, message = null)
        queryPurchases()
    }

    fun redeemLocalPlusCode(rawCode: String): Boolean {
        if (!isLocalPlusPromoCode(rawCode)) return false
        localPlusUnlocked = true
        localEntitlementPreferences.edit {
            putBoolean("local_plus_unlocked", true)
        }
        _state.value = _state.value.copy(
            isPremium = true,
            isLocalUnlock = true,
            isLoading = false,
            message = PremiumMessage.LOCAL_CODE_APPLIED,
        )
        return true
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when {
            billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null -> {
                processPurchases(purchases)
            }

            billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED -> {
                _state.value = _state.value.copy(isLoading = false, message = PremiumMessage.PURCHASE_CANCELED)
            }

            else -> {
                _state.value = _state.value.copy(
                    isLoading = false,
                    message = PremiumMessage.PURCHASE_FAILED,
                )
            }
        }
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        billingClient.queryProductDetailsAsync(params) { billingResult, result ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = result.productDetailsList.firstOrNull()
                _state.value = _state.value.copy(
                    productAvailable = productDetails != null,
                    price = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice,
                    message = if (productDetails == null) {
                        PremiumMessage.PRODUCT_UNAVAILABLE
                    } else {
                        null
                    },
                )
            } else {
                _state.value = _state.value.copy(
                    productAvailable = false,
                    price = null,
                    message = PremiumMessage.PRODUCT_LOAD_FAILED,
                )
            }
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    message = PremiumMessage.RESTORE_FAILED,
                )
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val matchingPurchases = purchases.filter { it.products.contains(PREMIUM_PRODUCT_ID) }
        val purchased = matchingPurchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val pending = matchingPurchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }
        billingPlusPurchased = purchased
        _state.value = _state.value.copy(
            isPremium = billingPlusPurchased || localPlusUnlocked,
            isLocalUnlock = localPlusUnlocked,
            isLoading = false,
            message = when {
                pending && !purchased && !localPlusUnlocked -> PremiumMessage.PENDING
                purchased || localPlusUnlocked -> null
                else -> _state.value.message
            },
        )

        matchingPurchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .forEach(::acknowledge)
    }

    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _state.value = _state.value.copy(
                    message = PremiumMessage.ACKNOWLEDGE_FAILED,
                )
            }
        }
    }
}
