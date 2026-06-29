package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdMobRewardedHelper {
    private var rewardedAd: RewardedAd? = null
    // Standard Google test rewarded ad unit ID
    private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    private var isLoading = false

    fun loadAd(context: Context, onLoaded: (Boolean) -> Unit = {}) {
        if (rewardedAd != null || isLoading) {
            onLoaded(rewardedAd != null)
            return
        }
        isLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e("AdMobRewardedHelper", "Ad failed to load: ${adError.message}")
                rewardedAd = null
                isLoading = false
                onLoaded(false)
            }

            override fun onAdLoaded(ad: RewardedAd) {
                Log.d("AdMobRewardedHelper", "Rewarded Ad was loaded successfully.")
                rewardedAd = ad
                isLoading = false
                onLoaded(true)
            }
        })
    }

    fun showAd(activity: Activity, onRewardEarned: (Int) -> Unit, onAdDismissed: () -> Unit) {
        rewardedAd?.let { ad ->
            ad.show(activity) { rewardItem ->
                val rewardAmount = rewardItem.amount
                // Default reward is usually 5 credits
                val finalReward = if (rewardAmount <= 0) 5 else rewardAmount
                onRewardEarned(finalReward)
            }
            rewardedAd = null
            loadAd(activity) // Auto preload next
            onAdDismissed()
        } ?: run {
            Log.e("AdMobRewardedHelper", "The rewarded ad wasn't ready yet.")
            loadAd(activity)
            onAdDismissed()
        }
    }

    fun isAdReady(): Boolean = rewardedAd != null
}
