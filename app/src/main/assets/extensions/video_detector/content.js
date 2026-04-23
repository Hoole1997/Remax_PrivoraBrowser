/**
 * Video Detector - Content Script
 * 监听页面中的 video 元素，检测视频源
 * 并将检测结果发送给 Android Native
 */

(function() {
    'use strict';
    
    // 是否是主框架（只有主框架才发送消息给 Android）
    const isMainFrame = (window === window.top);
    
    // 已检测的 URL 缓存
    const detectedUrls = new Set();
    
    // 已发送到 Native 的 URL 缓存（仅主框架使用）
    const sentToNativeUrls = new Set();
    
    // 视频文件后缀
    const VIDEO_SUFFIXES = ['mp4', 'm4v', 'm4p', 'mov', 'webm', 'flv', 'f4v', 'ogv', 'ogg', '3gp', 'mpg', 'mpeg', 'mkv', 'avi'];
    const AUDIO_SUFFIXES = ['mp3', 'm4a', 'wav', 'flac', 'aac', 'oga'];
    const STREAM_SUFFIXES = ['m3u8', 'm3u', 'mpd'];
    
    /**
     * 从 URL 中提取文件后缀
     */
    function getSuffix(url) {
        try {
            const urlObj = new URL(url);
            const pathname = urlObj.pathname;
            const lastDot = pathname.lastIndexOf('.');
            if (lastDot === -1) return null;
            return pathname.substring(lastDot + 1).toLowerCase().split('?')[0];
        } catch (e) {
            return null;
        }
    }
    
    /**
     * 检查 URL 是否为有效的媒体 URL
     */
    function isValidMediaUrl(url) {
        if (!url) return false;
        
        // 忽略 blob URL 和 data URL
        if (url.startsWith('blob:') || url.startsWith('data:')) {
            return false;
        }
        
        // 必须是 http/https
        if (!url.startsWith('http://') && !url.startsWith('https://')) {
            return false;
        }
        
        // 检查是否有媒体文件后缀
        const suffix = getSuffix(url);
        if (!suffix) return false;
        
        return VIDEO_SUFFIXES.includes(suffix) || 
               AUDIO_SUFFIXES.includes(suffix) || 
               STREAM_SUFFIXES.includes(suffix);
    }
    
    /**
     * 发送视频信息到 Android Native（只在主框架发送，且只发送一次）
     */
    function sendToNative(message) {
        // 只有主框架才发送消息给 Android
        if (!isMainFrame) return;
        
        // 获取视频 URL
        const url = message.data ? message.data.url : message.url;
        if (!url) return;
        
        // 检查是否已发送过
        if (sentToNativeUrls.has(url)) {
            return;
        }
        
        // 标记为已发送
        sentToNativeUrls.add(url);
        
        try {
            browser.runtime.sendNativeMessage('video_detector', message);
        } catch (e) {
            // 发送失败时不重试
        }
    }
    
    /**
     * 上报检测到的视频 URL 给 background script
     * background 会统一去重后再通知回来
     */
    function reportVideoUrl(url, type) {
        if (!isValidMediaUrl(url)) return;
        if (detectedUrls.has(url)) return;
        
        detectedUrls.add(url);
        
        // 通知 background script，由它统一处理去重
        browser.runtime.sendMessage({
            action: 'video_element_detected',
            url: url,
            type: type || 'video',
            pageUrl: window.location.href
        }).catch(() => {
            // 忽略错误
        });
    }
    
    /**
     * 检查 video 元素
     */
    function checkVideoElement(video) {
        // 检查 video.src
        if (video.src) {
            reportVideoUrl(video.src, 'video');
        }
        
        // 检查 video.currentSrc
        if (video.currentSrc && video.currentSrc !== video.src) {
            reportVideoUrl(video.currentSrc, 'video');
        }
        
        // 检查 source 子元素
        const sources = video.querySelectorAll('source');
        sources.forEach(source => {
            if (source.src) {
                reportVideoUrl(source.src, 'video');
            }
        });
    }
    
    /**
     * 检查 audio 元素
     */
    function checkAudioElement(audio) {
        if (audio.src) {
            reportVideoUrl(audio.src, 'audio');
        }
        
        if (audio.currentSrc && audio.currentSrc !== audio.src) {
            reportVideoUrl(audio.currentSrc, 'audio');
        }
        
        const sources = audio.querySelectorAll('source');
        sources.forEach(source => {
            if (source.src) {
                reportVideoUrl(source.src, 'audio');
            }
        });
    }
    
    /**
     * 扫描页面中的所有媒体元素
     */
    function scanMediaElements() {
        // 扫描 video 元素
        document.querySelectorAll('video').forEach(checkVideoElement);
        
        // 扫描 audio 元素
        document.querySelectorAll('audio').forEach(checkAudioElement);
    }
    
    /**
     * 监听 DOM 变化
     */
    function observeDOM() {
        const observer = new MutationObserver((mutations) => {
            mutations.forEach(mutation => {
                // 检查新增的节点
                mutation.addedNodes.forEach(node => {
                    if (node.nodeType !== Node.ELEMENT_NODE) return;
                    
                    // 检查节点本身
                    if (node.tagName === 'VIDEO') {
                        checkVideoElement(node);
                    } else if (node.tagName === 'AUDIO') {
                        checkAudioElement(node);
                    }
                    
                    // 检查子节点
                    if (node.querySelectorAll) {
                        node.querySelectorAll('video').forEach(checkVideoElement);
                        node.querySelectorAll('audio').forEach(checkAudioElement);
                    }
                });
                
                // 检查属性变化（src 变化）
                if (mutation.type === 'attributes' && mutation.attributeName === 'src') {
                    const target = mutation.target;
                    if (target.tagName === 'VIDEO') {
                        checkVideoElement(target);
                    } else if (target.tagName === 'AUDIO') {
                        checkAudioElement(target);
                    } else if (target.tagName === 'SOURCE') {
                        const parent = target.parentElement;
                        if (parent && parent.tagName === 'VIDEO') {
                            checkVideoElement(parent);
                        } else if (parent && parent.tagName === 'AUDIO') {
                            checkAudioElement(parent);
                        }
                    }
                }
            });
        });
        
        observer.observe(document.documentElement, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['src']
        });
    }
    
    /**
     * 拦截 video.src 设置（捕获动态设置的 src）
     */
    function interceptVideoSrc() {
        // 拦截 HTMLVideoElement.src
        const videoSrcDescriptor = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
        if (videoSrcDescriptor && videoSrcDescriptor.set) {
            const originalSet = videoSrcDescriptor.set;
            Object.defineProperty(HTMLMediaElement.prototype, 'src', {
                ...videoSrcDescriptor,
                set: function(value) {
                    originalSet.call(this, value);
                    if (this.tagName === 'VIDEO') {
                        reportVideoUrl(value, 'video');
                    } else if (this.tagName === 'AUDIO') {
                        reportVideoUrl(value, 'audio');
                    }
                }
            });
        }
    }
    
    // 初始化
    function init() {
        // 扫描现有元素
        scanMediaElements();
        
        // 监听 DOM 变化
        observeDOM();
        
        // 拦截 src 设置
        try {
            interceptVideoSrc();
        } catch (e) {
            // 某些页面可能不允许修改原型
        }
        
        // 监听来自 background script 的消息
        browser.runtime.onMessage.addListener((message) => {
            if (message.action === 'video_detected_from_background') {
                // 转发给 Android Native
                sendToNative(message.data);
            }
        });
    }
    
    // 等待 DOM 加载完成
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
    
})();
