(function(window, document) {
  // 获取参数
  function getParam(name) {
    try {
      var searchParams = new URLSearchParams(window.location.search);
      if (searchParams.has(name)) {
        return searchParams.get(name);
      } else {
        return '';
      }
    } catch (e) {}
  }

  // 获取搜索关键词
  function getKeyInEvent() {
    var q = getParam("q");
    try {
      return q.replaceAll(" ", "+");
    } catch (e) {
      return q;
    }
  }

  // 过滤事件值
  function filterEventValue(value) {
    var filtered = {};
    for (var key in value) {
      if (value[key]) {
        filtered[key] = value[key];
      }
    }
    return filtered;
  }

  // GA 事件上报
  function fireGtagEvent(eventName, extraData) {
    if (typeof gtag !== "function") {
      return;
    }

    var channel = getParam("channel") || "organic";
    var value = {
      keyword: getKeyInEvent(),
      channel: channel,
      network: getParam('network'),
      ...extraData
    };

    gtag("event", eventName, filterEventValue(value));
  }

  // 广告展示事件上报
  function adViewAction() {
    fireGtagEvent("ad_loaded");
  }

  // 广告点击事件上报
  function adClickAction() {
    fireGtagEvent("ad_click");
  }

  // 搜索结果点击事件上报
  function bindResultClickTracking(resultElts) {
    try {
      for (var i = 0; i < resultElts.length; i++) {
        var resultEl = resultElts[i];
        if (resultEl && !resultEl.__clickTracked) {
          resultEl.__clickTracked = true;
          resultEl.addEventListener("click", function() {
            fireGtagEvent("content_click");
          });
        }
      }
    } catch (e) {}
  }

  // 监听搜索结果是否有广告返回
  function observeAdRenderOnce() {
    var targetNode = document.querySelector(".gsc-wrapper");
    if (!targetNode || window.__cseAdObserved) {
      return;
    }

    window.__cseAdObserved = true;
    var observer = new MutationObserver(function(mutationsList) {
      if (window.__cseAdLoaded) {
        observer.disconnect();
        return;
      }

      for (var i = 0; i < mutationsList.length; i++) {
        var mutation = mutationsList[i];
        var target = mutation.target;
        if (target && target.nodeName === "IFRAME") {
          var iframeRect = target.getBoundingClientRect();
          if (iframeRect.height > 0 || iframeRect.width > 0) {
            window.__cseAdLoaded = true;
            adViewAction();
            observer.disconnect();
            break;
          }
        }
      }
    });

    observer.observe(targetNode, {
      attributes: true,
      childList: true,
      subtree: true
    });
  }

  // 广告点击事件
  window.onmessage = function(e) {
    try {
      var data = e && e.data;
      if (data && data.startsWith("FSXDC,.aCS:")) {
        adClickAction();
      }
      if (data && data.startsWith("FSXDC,irt:") && !window.__cseAdLoaded) {
        window.__cseAdLoaded = true;
        adViewAction();
      }
    } catch (err) {}
  };

  // 搜索结果渲染成功的事件回调
  window.__gcse = {
    parsetags: "onload",
    initializationCallback: null,
    searchCallbacks: {
      web: {
        rendered: function(gname, query, promoElts, resultElts) {
          observeAdRenderOnce();
          bindResultClickTracking(resultElts || []);
          fireGtagEvent("cse_rendered", {
            rendered_query: query,
            rendered_name: gname
          });
        }
      }
    }
  };

  // 搜索结果渲染，设置channel参数，用于区分收益
  window.renderResults = function() {
    var channel = getParam("channel");
    var option = {
      div: "search-results",
      tag: "searchresults-only",
      gname: "gsearch",
      attributes: {
        safeSearch: "off"
      }
    };

    if (channel) {
      option.attributes.adChannel = channel;
    }

    google.search.cse.element.render(option);
  };

  // 获取搜索结果页面URL,用于related search 目标地址
  function getResultPageUrl() {
    var resultPage = `${window.location.origin}/search/?`;
    var params = new URLSearchParams(window.location.search);
    let queryArr = ['channel', 'network'];
    var newParams = new URLSearchParams();
    for (let i = 0; i < queryArr.length; i++) {
      let query = queryArr[i];
      if (params.has(query)) {
        newParams.set(query, params.get(query));
      }
    }
    resultPage += newParams.toString();
    return resultPage;
  }

  // related search 渲染成功的事件回调
  var _rsLoaded = false;
  window.RSLoaded = function(a, b, c, d) {
    if (b) {
      try {
        if (!_rsLoaded) {
          fireGtagEvent('rs_loaded');
        }
        _rsLoaded = true;
      } catch (e) {}
    }
  };

  // related search 渲染
  function renderRS() {
    let q = getParam('q');
    if (!q) {
      return
    }
    var resultPage = getResultPageUrl();
    var ivt = false;
    if (getParam('ivt') == 'true') {
      ivt = true;
    }
    // console.log(window.RSLoaded)
    var rsPageOptions = {
      "pubId": 'pub-5149440008061651',
      "relatedSearchTargeting": 'query',
      "resultsPageBaseUrl": resultPage,
      // "styleId": '3476787859',
      "query": q,
      "resultsPageQueryParam": 'q',
      "adsafe": 'low',
      "linkTarget": "_blank",
      "oe": "utf-8",
      "ie": "utf-8",
      "ivt": ivt,
      "adPage": 1,
      "adLoadedCallback": window.RSLoaded || null,
      "adtest": "on",
    };

    var channel = getParam('channel');
    if (channel) {
      rsPageOptions.channel = channel;
    }

    var rsOption = {
      "container": "related-search",
      "relatedSearches": 8
    };

    _googCsa('relatedsearch', rsPageOptions, rsOption);
  }
  renderRS();

  // 搜索页面渲染成功的事件上报
  fireGtagEvent("searchresult_page_view");
})(window, document);
