package com.android.common.bill.ads.bidding

import android.app.Activity
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.admob.AdmobAppOpenAdController
import com.android.common.bill.ads.gam.GamAppOpenAdController
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.pangle.PangleAppOpenAdController
import com.android.common.bill.ads.topon.TopOnSplashAdController
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.tracker.AdEventReporter
import com.android.common.bill.ads.util.AdmobNextGenReflectionUtil
import com.android.common.bill.ads.util.GamNextGenReflectionUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.Locale

/**
 * 开屏广告竞价控制器
 * 同时加载 AdMob、GAM、Pangle、TopOn，比较收益后选择展示
 */
object AppOpenBiddingManager {

    suspend fun bidding(
        activity: Activity,
        admobAdUnitId: String = BillConfig.admob.splashId,
        gamAdUnitId: String = BillConfig.gam.splashId,
        pangleAdUnitId: String = BillConfig.pangle.splashId,
        toponPlacementId: String = BillConfig.topon.splashId,
        position: String? = null,
    ): BiddingResult {
        // 检查固定聚合源（包含初始化和频控检查）
        when (val result = AdSourceController.checkFixedSource(AdType.APP_OPEN)) {
            is AdSourceController.FixedSourceCheckResult.UseFixedSource -> {
                return BiddingResult(result.winner, 0.0)
            }
            is AdSourceController.FixedSourceCheckResult.UseBidding -> {
                // 继续使用竞价逻辑
            }
        }
        
        return performBidding(activity, admobAdUnitId, gamAdUnitId, pangleAdUnitId, toponPlacementId, position)
    }

    private suspend fun performBidding(
        activity: Activity,
        admobAdUnitId: String,
        gamAdUnitId: String,
        pangleAdUnitId: String,
        toponPlacementId: String,
        position: String?,
    ): BiddingResult {
        val context = activity.applicationContext
        val admobController = AdmobAppOpenAdController.getInstance()
        val gamController = GamAppOpenAdController.getInstance()
        val pangleController = PangleAppOpenAdController.getInstance()
        val toponController = TopOnSplashAdController.getInstance()
        
        // 根据平台配置决定是否参与比价
        val admobConfigEnabled = BiddingPlatformController.isAdmobEnabled(AdType.APP_OPEN)
        val gamConfigEnabled = BiddingPlatformController.isGamEnabled(AdType.APP_OPEN)
        val pangleConfigEnabled = BiddingPlatformController.isPangleEnabled(AdType.APP_OPEN)
        val toponConfigEnabled = BiddingPlatformController.isToponEnabled(AdType.APP_OPEN)
        
        // 检查频率限制，过滤掉被限制的平台
        val admobEnabled = admobConfigEnabled && BiddingExclusionController.canPlatformBid(AdType.APP_OPEN, AdPlatform.ADMOB)
        val gamEnabled = gamConfigEnabled && BiddingExclusionController.canPlatformBid(AdType.APP_OPEN, AdPlatform.GAM)
        val pangleEnabled = pangleConfigEnabled && BiddingExclusionController.canPlatformBid(AdType.APP_OPEN, AdPlatform.PANGLE)
        val toponEnabled = toponConfigEnabled && BiddingExclusionController.canPlatformBid(AdType.APP_OPEN, AdPlatform.TOPON)

        // 生成竞价唯一ID
        val bidId = BiddingTracker.generateBidId()
        
        // 构建参与平台列表
        val platformList = mutableListOf<AdPlatform>()
        if (admobEnabled) platformList.add(AdPlatform.ADMOB)
        if (gamEnabled) platformList.add(AdPlatform.GAM)
        if (pangleEnabled) platformList.add(AdPlatform.PANGLE)
        if (toponEnabled) platformList.add(AdPlatform.TOPON)
        
        // 上报竞价开始
        val startTime = System.currentTimeMillis()
        AdLogger.d("============= 开屏广告竞价开始 =============")
        BiddingTracker.reportBidStart(bidId, AdType.APP_OPEN, platformList)
        if (platformList.isEmpty()) {
            BiddingTracker.reportBidFailNoPlatform(bidId, AdType.APP_OPEN, "no_platform_available_after_exclusion")
            AdLogger.w("开屏广告无可参与竞价平台（初始化/配置/频控后全部不可用）")
            return BiddingResult(BiddingWinner.ADMOB, 0.0)
        }

        // 缓存优先：检查各平台缓存状态
        val admobHasCache = admobEnabled && admobController.getCachedAdPeek(admobAdUnitId) != null
        val gamHasCache = gamEnabled && gamController.getCachedAdPeek(gamAdUnitId) != null
        val pangleHasCache = pangleEnabled && pangleController.getCachedAdPeek(pangleAdUnitId) != null
        val toponHasCache = toponEnabled && toponController.getCachedAdPeek(toponPlacementId) != null
        val anyCached = admobHasCache || gamHasCache || pangleHasCache || toponHasCache

        val needLoadPlatforms = mutableListOf<AdPlatform>()
        if (admobEnabled && !admobHasCache) needLoadPlatforms.add(AdPlatform.ADMOB)
        if (gamEnabled && !gamHasCache) needLoadPlatforms.add(AdPlatform.GAM)
        if (pangleEnabled && !pangleHasCache) needLoadPlatforms.add(AdPlatform.PANGLE)
        if (toponEnabled && !toponHasCache) needLoadPlatforms.add(AdPlatform.TOPON)

        AdLogger.d("开屏缓存状态 -> AdMob:$admobHasCache, GAM:$gamHasCache, Pangle:$pangleHasCache, TopOn:$toponHasCache, anyCached:$anyCached, 需加载:${needLoadPlatforms.map { it.key }}")

        // 上报无缓存平台
        if (admobEnabled && !admobHasCache) AdEventReporter.reportNoCache(AdType.APP_OPEN, AdPlatform.ADMOB, admobAdUnitId, position = position)
        if (gamEnabled && !gamHasCache) AdEventReporter.reportNoCache(AdType.APP_OPEN, AdPlatform.GAM, gamAdUnitId, position = position)
        if (pangleEnabled && !pangleHasCache) AdEventReporter.reportNoCache(AdType.APP_OPEN, AdPlatform.PANGLE, pangleAdUnitId, position = position)
        if (toponEnabled && !toponHasCache) AdEventReporter.reportNoCache(AdType.APP_OPEN, AdPlatform.TOPON, toponPlacementId, position = position)

        if (needLoadPlatforms.isNotEmpty()) {
            if (anyCached) {
                // 有缓存：非缓存平台后台fire-and-forget预加载，不阻塞竞价
                AdLogger.d("开屏有缓存平台，非缓存平台后台预加载，秒出竞价结果")
                val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                for (platform in needLoadPlatforms) {
                    bgScope.launch {
                        runCatching {
                            when (platform) {
                                AdPlatform.ADMOB -> AdEventReporter.withLoadPosition(position) {
                                    admobController.preloadAd(context, admobAdUnitId)
                                }
                                AdPlatform.GAM -> AdEventReporter.withLoadPosition(position) {
                                    gamController.preloadAd(context, gamAdUnitId)
                                }
                                AdPlatform.PANGLE -> AdEventReporter.withLoadPosition(position) {
                                    pangleController.preloadAd(context, pangleAdUnitId)
                                }
                                AdPlatform.TOPON -> AdEventReporter.withLoadPosition(position) {
                                    toponController.preloadAd(context, toponPlacementId)
                                }
                            }
                        }
                    }
                }
            } else {
                // 全无缓存：并行加载+7s超时
                supervisorScope {
                    val admobDeferred = if (AdPlatform.ADMOB in needLoadPlatforms) async {
                        runCatching {
                            AdEventReporter.withLoadPosition(position) {
                                admobController.preloadAd(context, admobAdUnitId)
                            }
                        }.getOrNull()
                    } else null
                    val gamDeferred = if (AdPlatform.GAM in needLoadPlatforms) async {
                        runCatching {
                            AdEventReporter.withLoadPosition(position) {
                                gamController.preloadAd(context, gamAdUnitId)
                            }
                        }.getOrNull()
                    } else null
                    val pangleDeferred = if (AdPlatform.PANGLE in needLoadPlatforms) async {
                        runCatching {
                            AdEventReporter.withLoadPosition(position) {
                                pangleController.preloadAd(context, pangleAdUnitId)
                            }
                        }.getOrNull()
                    } else null
                    val toponDeferred = if (AdPlatform.TOPON in needLoadPlatforms) async {
                        runCatching {
                            AdEventReporter.withLoadPosition(position) {
                                toponController.preloadAd(context, toponPlacementId)
                            }
                        }.getOrNull()
                    } else null

                    awaitBidPreloadsWithTimeout(
                        timeoutMs = 7000L,
                        timeoutLog = "开屏广告竞价加载超时(7s)，取消未完成的加载任务并使用已完成的结果",
                        admobDeferred,
                        gamDeferred,
                        pangleDeferred,
                        toponDeferred
                    )
                }
            }
        } else {
            AdLogger.d("开屏所有启用平台都有缓存，跳过加载")
        }

        // 计算竞价耗时
        val biddingDuration = System.currentTimeMillis() - startTime

        // 上报加载后仍无缓存的平台为竞价失败
        if (admobEnabled && admobController.getCachedAdPeek(admobAdUnitId) == null) {
            BiddingTracker.reportBidFail(bidId, AdPlatform.ADMOB, "no_cache_after_load")
        }
        if (gamEnabled && gamController.getCachedAdPeek(gamAdUnitId) == null) {
            BiddingTracker.reportBidFail(bidId, AdPlatform.GAM, "no_cache_after_load")
        }
        if (pangleEnabled && pangleController.getCachedAdPeek(pangleAdUnitId) == null) {
            BiddingTracker.reportBidFail(bidId, AdPlatform.PANGLE, "no_cache_after_load")
        }
        if (toponEnabled && toponController.getCachedAdPeek(toponPlacementId) == null) {
            BiddingTracker.reportBidFail(bidId, AdPlatform.TOPON, "no_cache_after_load")
        }

        // 从缓存获取各平台 eCPM（直接查缓存，不依赖加载结果状态）
        val admobValueUsd = if (admobEnabled) {
            admobController.getCachedAdPeek(admobAdUnitId)?.ad?.let { ad ->
                AdmobNextGenReflectionUtil.getRevenueByPath(ad)?.valueMicros?.toDouble()?.div(1_000_000.0)
            } ?: 0.0
        } else 0.0

        val gamValueUsd = if (gamEnabled) {
            gamController.getCachedAdPeek(gamAdUnitId)?.ad?.let { ad ->
                GamNextGenReflectionUtil.getRevenueByPath(ad)?.valueMicros?.toDouble()?.div(1_000_000.0)
            } ?: 0.0
        } else 0.0

        val pangleValueUsd = if (pangleEnabled) {
            pangleController.getCachedAdPeek(pangleAdUnitId)?.winEcpm?.revenue?.toDoubleOrNull() ?: 0.0
        } else 0.0

        val toponValueUsd = if (toponEnabled) {
            toponController.getCachedAdPeek(toponPlacementId)?.let { splashAd ->
                runCatching { splashAd.checkValidAdCaches().firstOrNull()?.publisherRevenue }.getOrNull() ?: 0.0
            } ?: 0.0
        } else 0.0

        val biddingLog = String.format(
            Locale.US,
            "开屏竞价结果 -> AdMob: %.8f 美元%s, GAM: %.8f 美元%s, Pangle: %.8f 美元%s, TopOn: %.8f 美元%s",
            admobValueUsd, if (admobEnabled) "" else "(禁用)",
            gamValueUsd, if (gamEnabled) "" else "(禁用)",
            pangleValueUsd, if (pangleEnabled) "" else "(禁用)",
            toponValueUsd, if (toponEnabled) "" else "(禁用)",
        )
        AdLogger.d(biddingLog)
        BiddingTracker.reportBiddingLog(biddingLog)

        // 只在启用的平台中选择胜出者
        // 比较所有平台的收益，选择最高的
        val candidates = mutableListOf<Pair<BiddingWinner, Double>>()
        if (admobEnabled) candidates.add(BiddingWinner.ADMOB to admobValueUsd)
        if (gamEnabled) candidates.add(BiddingWinner.GAM to gamValueUsd)
        if (pangleEnabled) candidates.add(BiddingWinner.PANGLE to pangleValueUsd)
        if (toponEnabled) candidates.add(BiddingWinner.TOPON to toponValueUsd)

        val winner = candidates.maxByOrNull { it.second }?.first ?: BiddingWinner.ADMOB
        val winnerCpm = candidates.maxByOrNull { it.second }?.second ?: 0.0
        
        // 上报竞价胜出
        val winnerPlatform = when (winner) {
            BiddingWinner.ADMOB -> AdPlatform.ADMOB
            BiddingWinner.GAM -> AdPlatform.GAM
            BiddingWinner.PANGLE -> AdPlatform.PANGLE
            BiddingWinner.TOPON -> AdPlatform.TOPON
        }
        BiddingTracker.reportBidWin(bidId, winnerPlatform, biddingDuration, winnerCpm)
        AdLogger.d("============= 开屏广告竞价结束 =============")

        return BiddingResult(winner, winnerCpm)
    }
}
