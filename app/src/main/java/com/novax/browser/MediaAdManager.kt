package com.novax.browser

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class MediaAdManager(private val context: Context) {

    private val adUnitId = "ca-app-pub-3940256099942544/5224354917"
    private var adSlot1: RewardedAd? = null
    private var adSlot2: RewardedAd? = null

    fun preloadAds() {
        fetchSlot1()
        fetchSlot2()
    }

    private fun fetchSlot1() {
        RewardedAd.load(context, adUnitId, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { adSlot1 = ad }
            override fun onAdFailedToLoad(error: LoadAdError) { adSlot1 = null }
        })
    }

    private fun fetchSlot2() {
        RewardedAd.load(context, adUnitId, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { adSlot2 = ad }
            override fun onAdFailedToLoad(error: LoadAdError) { adSlot2 = null }
        })
    }

    fun startAdSequenceAndOpen(activity: Activity, onComplete: () -> Unit) {
        if (adSlot1 != null) {
            Toast.makeText(context, "Sponsor Ad (1/2)", Toast.LENGTH_SHORT).show()
            adSlot1?.show(activity) {
                adSlot1 = null
                fetchSlot1()

                if (adSlot2 != null) {
                    Toast.makeText(context, "Sponsor Ad (2/2)", Toast.LENGTH_SHORT).show()
                    adSlot2?.show(activity) {
                        adSlot2 = null
                        fetchSlot2()
                        onComplete()
                    }
                } else {
                    onComplete()
                    fetchSlot2()
                }
            }
        } else {
            onComplete()
            preloadAds()
        }
    }
}
