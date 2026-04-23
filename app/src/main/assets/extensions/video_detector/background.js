/**
 * Video Detector - Background Script
 * 使用 webRequest API 监听网络请求，识别视频链接
 * 检测到视频后立即通知对应标签页的 content script
 */

// 视频文件后缀
const VIDEO_SUFFIXES = ['mp4', 'm4v', 'm4p', 'mov', 'webm', 'flv', 'f4v', 'ogv', 'ogg', '3gp', 'mpg', 'mpeg', 'mkv', 'avi'];

// 音频文件后缀
const AUDIO_SUFFIXES = ['mp3', 'm4a', 'wav', 'flac', 'aac', 'oga'];

// 流媒体后缀
const STREAM_SUFFIXES = ['m3u8', 'm3u', 'mpd'];

// 忽略的分片后缀
const SEGMENT_SUFFIXES = ['ts', 'm4s'];

// M3U8 MIME 类型
const M3U8_MIMES = [
    'application/vnd.apple.mpegurl',
    'audio/mpegurl',
    'application/x-mpegurl',
    'audio/x-mpegurl'
];

// 按标签页存储已检测的视频 URL（tabId -> Set<url>）
const tabVideoCache = new Map();

// 缓存大小限制（每个标签页）
const MAX_CACHE_PER_TAB = 100;

/**
 * 从 URL 中提取文件后缀
 */
function getSuffix(url) {
    try {
        const urlObj = new URL(url);
        const pathname = urlObj.pathname;
        const lastDot = pathname.lastIndexOf('.');
        if (lastDot === -1) return null;
        
        let suffix = pathname.substring(lastDot + 1).toLowerCase();
        // 移除可能的查询参数残留
        const queryIndex = suffix.indexOf('?');
        if (queryIndex !== -1) {
            suffix = suffix.substring(0, queryIndex);
        }
        return suffix;
    } catch (e) {
        return null;
    }
}

/**
 * 从 Content-Type 中提取 MIME 类型
 */
function getMimeType(contentType) {
    if (!contentType) return null;
    const semicolonIndex = contentType.indexOf(';');
    return contentType.substring(0, semicolonIndex === -1 ? contentType.length : semicolonIndex).trim().toLowerCase();
}

/**
 * 检测 URL 是否为视频链接
 * @returns {object|null} 返回媒体信息或 null
 */
function detectVideo(url, responseHeaders) {
    // 获取 Content-Type
    let contentType = null;
    let contentLength = null;
    
    if (responseHeaders) {
        for (const header of responseHeaders) {
            const name = header.name.toLowerCase();
            if (name === 'content-type') {
                contentType = header.value;
            } else if (name === 'content-length') {
                contentLength = header.value;
            }
        }
    }
    
    const mime = getMimeType(contentType);
    const suffix = getSuffix(url);
    
    // 忽略分片文件
    if (suffix && SEGMENT_SUFFIXES.includes(suffix)) {
        return null;
    }
    
    // 忽略 TS 流 MIME
    if (mime === 'video/mp2t') {
        return null;
    }
    
    let mediaType = null;
    
    // 1. 通过 MIME 类型识别
    if (mime) {
        if (M3U8_MIMES.includes(mime)) {
            mediaType = 'm3u8';
        } else if (mime.startsWith('video/')) {
            mediaType = 'video';
        } else if (mime.startsWith('audio/')) {
            mediaType = 'audio';
        }
    }
    
    // 2. 通过后缀识别（MIME 未识别时）
    if (!mediaType && suffix) {
        if (STREAM_SUFFIXES.includes(suffix)) {
            mediaType = 'm3u8';
        } else if (VIDEO_SUFFIXES.includes(suffix)) {
            mediaType = 'video';
        } else if (AUDIO_SUFFIXES.includes(suffix)) {
            mediaType = 'audio';
        }
    }
    
    // 3. 特殊处理：application/octet-stream 时通过后缀判断
    if (!mediaType && mime === 'application/octet-stream' && suffix) {
        if (VIDEO_SUFFIXES.includes(suffix)) {
            mediaType = 'video';
        } else if (AUDIO_SUFFIXES.includes(suffix)) {
            mediaType = 'audio';
        } else if (STREAM_SUFFIXES.includes(suffix)) {
            mediaType = 'm3u8';
        }
    }
    
    if (mediaType) {
        return {
            url: url,
            type: mediaType,
            mime: mime,
            suffix: suffix,
            size: contentLength ? parseInt(contentLength) : null
        };
    }
    
    return null;
}

/**
 * 检查并缓存视频 URL（按标签页去重）
 * @returns {boolean} 如果是新视频返回 true，否则返回 false
 */
function cacheVideoForTab(tabId, url) {
    if (!tabVideoCache.has(tabId)) {
        tabVideoCache.set(tabId, new Set());
    }
    
    const cache = tabVideoCache.get(tabId);
    if (cache.has(url)) {
        return false;
    }
    
    cache.add(url);
    
    // 缓存大小限制
    if (cache.size > MAX_CACHE_PER_TAB) {
        const firstItem = cache.values().next().value;
        cache.delete(firstItem);
    }
    
    return true;
}

/**
 * 通知 content script 检测到视频
 */
function notifyContentScript(videoInfo, source) {
    const tabId = videoInfo.tabId;
    if (!tabId || tabId < 0) return;
    
    // 检查是否已经通知过
    if (!cacheVideoForTab(tabId, videoInfo.url)) {
        return;
    }
    
    const message = {
        action: 'video_detected',
        source: source,
        data: videoInfo
    };
    
    // 通知对应标签页的主框架 content script
    browser.tabs.sendMessage(tabId, {
        action: 'video_detected_from_background',
        data: message
    }, { frameId: 0 }).catch(() => {
        // 忽略发送失败（标签页可能已关闭或 content script 未加载）
    });
}

/**
 * 清理已关闭标签页的缓存
 */
browser.tabs.onRemoved.addListener((tabId) => {
    tabVideoCache.delete(tabId);
});

/**
 * 标签页导航时清理缓存
 */
browser.webNavigation.onCommitted.addListener((details) => {
    if (details.frameId === 0) {
        // 主框架导航，清理该标签页的缓存
        tabVideoCache.delete(details.tabId);
    }
});

/**
 * 监听网络请求响应头
 */
browser.webRequest.onHeadersReceived.addListener(
    function(details) {
        // 忽略非标签页请求
        if (details.tabId < 0) {
            return;
        }
        
        // 只处理成功的响应
        if (details.statusCode < 200 || details.statusCode >= 300) {
            if (details.statusCode !== 304) {
                return;
            }
        }
        
        // 检测视频
        const videoInfo = detectVideo(details.url, details.responseHeaders);
        
        if (videoInfo) {
            videoInfo.tabId = details.tabId;
            notifyContentScript(videoInfo, 'webRequest');
        }
    },
    { urls: ['<all_urls>'] },
    ['responseHeaders']
);

console.log('[VideoDetector] webRequest listener registered');

/**
 * 接收来自 content script 的消息
 */
browser.runtime.onMessage.addListener((message, sender) => {
    if (message.action === 'video_element_detected') {
        // 只处理主框架的消息
        if (sender.frameId !== 0) return;
        
        const videoInfo = {
            url: message.url,
            type: message.type || 'video',
            mime: null,
            suffix: getSuffix(message.url),
            size: null,
            tabId: sender.tab ? sender.tab.id : -1,
            pageUrl: message.pageUrl
        };
        // 直接通知 content script（会自动去重）
        notifyContentScript(videoInfo, 'content_script');
    }
});

console.log('[VideoDetector] Background script loaded');
