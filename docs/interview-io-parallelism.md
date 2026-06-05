# 技术面试题：串行 I/O 的性能瓶颈与并发优化

## 问题背景

在 LoL 赛事快报生成模块中，需要为当天每场比赛从虎扑论坛抓取对应的赛后帖（HTTP 请求），再将比赛数据 + 帖子内容一起送给 LLM 生成短评。

核心代码路径：`LolEsportsRecapServiceImpl.buildUserPrompt()`

---

## 发现了什么问题？

原始实现使用普通 `stream().map()` 依次处理每场比赛：

```java
List<Map<String, Object>> payload = matches.stream()
    .map(match -> {
        Map<String, Object> item = buildBaseItem(match);
        // 每次 map 都同步发起一次 HTTP 请求
        hupuLolPostService.findMatchPost(match).ifPresent(post -> {
            item.put("hupuTitle", post.getTitle());
            item.put("hupuBody",  post.getArticleBody());
        });
        return item;
    })
    .toList();
```

**这是串行 I/O**。如果当天有 N 场比赛，总耗时 = N × 单次请求耗时。  
假设每次虎扑请求平均 800 ms，4 场比赛就要等 ~3.2 秒，才能进入 LLM 调用阶段。

---

## 为什么是瓶颈？

这类场景的特征：

- 各请求之间**没有数据依赖**，A 场比赛的帖子不需要等 B 场的结果
- 操作是 **I/O 密集型**（网络请求），等待期间 CPU 完全空转
- 串行执行让总延迟随比赛数线性增长：`T_total = N × T_single`

这是一个典型的**可并发但被串行化**的反模式。

---

## 如何优化？

### 方案一：`parallelStream()`（最简单）

```java
List<Map<String, Object>> payload = matches.parallelStream()
    .map(match -> {
        Map<String, Object> item = buildBaseItem(match);
        hupuLolPostService.findMatchPost(match).ifPresent(post -> {
            item.put("hupuTitle", post.getTitle());
            item.put("hupuBody",  post.getArticleBody());
        });
        return item;
    })
    .toList();
```

优点：改动最小。  
**缺点**：底层使用公共 `ForkJoinPool.commonPool()`，在高并发 Web 服务中会与其他请求竞争线程，存在线程池污染风险，不推荐生产。

---

### 方案二：`CompletableFuture` + 专用线程池（推荐）

```java
// 专用线程池，隔离 I/O 等待，不污染公共池
private static final Executor IO_POOL =
    Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "hupu-fetch-");
        t.setDaemon(true);
        return t;
    });

// 并发提交所有请求
List<CompletableFuture<Map<String, Object>>> futures = matches.stream()
    .map(match -> CompletableFuture.supplyAsync(() -> {
        Map<String, Object> item = buildBaseItem(match);
        hupuLolPostService.findMatchPost(match).ifPresent(post -> {
            item.put("hupuTitle", post.getTitle());
            item.put("hupuBody",  post.getArticleBody());
        });
        return item;
    }, IO_POOL))
    .toList();

// 等待全部完成，保持原始顺序
List<Map<String, Object>> payload = futures.stream()
    .map(CompletableFuture::join)
    .toList();
```

**效果**：N 个请求同时发出，总耗时 ≈ 单次最慢请求的耗时，从 `O(N)` 降到 `O(1)`。

---

## 两种方案对比

| 维度 | `parallelStream` | `CompletableFuture` + 专用池 |
|---|---|---|
| 改动量 | 极小 | 中等 |
| 线程池控制 | 无（公共池） | 完全自控 |
| 超时/取消支持 | 无 | 支持 `.orTimeout()` |
| 生产适用性 | 低（有污染风险） | **高** |
| 异常处理 | stream 直接抛出 | 可 `.exceptionally()` 单独容错 |

---

## 一句话总结（面试收尾用）

> 问题本质是**把可并发的 I/O 操作串行化了**，导致总延迟随数据规模线性增长。  
> 用 `CompletableFuture` + 专用线程池，让所有外部请求同时发出，把 `O(N)` 的串行等待压缩到 `O(1)` 的并发等待，同时通过隔离线程池避免影响服务整体吞吐。
