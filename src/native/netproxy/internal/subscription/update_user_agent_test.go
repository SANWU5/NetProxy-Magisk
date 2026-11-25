package subscription

import (
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"slices"
	"sync"
	"testing"
	"time"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/catalog"
)

const (
	// 默认标识（sing-box）下常见的响应：只有配置模板，没有任何节点服务器。
	templateWithoutNodes = `{"dns":{"servers":[]},"outbounds":[{"type":"selector","tag":"proxy","outbounds":["direct"]},{"type":"direct","tag":"direct"}]}`
	// 换用 clash.meta 后得到的 Clash 配置。
	clashWithOneNode = "proxies:\n  - name: yaml-node\n    type: ss\n    server: 127.0.0.1\n    port: 8388\n    cipher: aes-128-gcm\n    password: secret\n"
	// 能直接解析出节点的 sing-box 格式。
	boxWithOneNode = `{"outbounds":[{"type":"socks","tag":"json-node","server":"127.0.0.1","server_port":1080}]}`
)

// userAgentServer 记录每次请求的 User-Agent，并按它决定响应。
type userAgentServer struct {
	*httptest.Server
	mutex sync.Mutex
	seen  []string
}

func newUserAgentServer(t *testing.T, respond func(userAgent string, writer http.ResponseWriter)) *userAgentServer {
	t.Helper()
	server := &userAgentServer{}
	server.Server = httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		userAgent := request.Header.Get("User-Agent")
		server.mutex.Lock()
		server.seen = append(server.seen, userAgent)
		server.mutex.Unlock()
		respond(userAgent, writer)
	}))
	t.Cleanup(server.Close)
	return server
}

func (server *userAgentServer) agents() []string {
	server.mutex.Lock()
	defer server.mutex.Unlock()
	return slices.Clone(server.seen)
}

// userAgentFixture 写入一个订阅分组；mutate 可在保存前调整元数据。
func userAgentFixture(t *testing.T, url string, mutate func(*catalog.Metadata)) (root, metaPath string) {
	t.Helper()
	root = t.TempDir()
	metaPath = filepath.Join(root, "fixture", "meta.json")
	metadata := catalog.NewMetadata("fixture", "Fixture", "subscription", url, time.Unix(1700000000, 0))
	metadata.UpdateViaProxy = "never"
	if mutate != nil {
		mutate(&metadata)
	}
	if err := catalog.SaveMetadataAtomic(t.Context(), metaPath, metadata); err != nil {
		t.Fatal(err)
	}
	return root, metaPath
}

func runUpdate(t *testing.T, root string, offset time.Duration) (Result, error) {
	t.Helper()
	return Update(t.Context(), UpdateOptions{
		Root: root, GroupID: "fixture", ProgressDir: filepath.Join(root, "progress"),
		Now: time.Unix(1700000000, 0).Add(offset),
	})
}

func loadFixtureMetadata(t *testing.T, metaPath string) catalog.Metadata {
	t.Helper()
	metadata, err := catalog.LoadMetadata(t.Context(), metaPath, "fixture")
	if err != nil {
		t.Fatal(err)
	}
	return metadata
}

func hasDiagnostic(metadata catalog.Metadata, code string) bool {
	for _, diagnostic := range metadata.LastDiagnostics {
		if diagnostic.Code == code {
			return true
		}
	}
	return false
}

// 默认标识只拿到模板时自动换用 clash.meta，并记住它，之后的更新不再多发请求。
func TestUpdateDetectsUserAgentAndRemembersIt(t *testing.T) {
	server := newUserAgentServer(t, func(userAgent string, writer http.ResponseWriter) {
		if userAgent == "clash.meta" {
			writer.Header().Set("Content-Type", "text/html; charset=UTF-8")
			_, _ = writer.Write([]byte(clashWithOneNode))
			return
		}
		_, _ = writer.Write([]byte(templateWithoutNodes))
	})
	root, metaPath := userAgentFixture(t, server.URL, nil)

	result, err := runUpdate(t, root, 0)
	if err != nil || result.NodeCount != 1 {
		t.Fatalf("自动换用 User-Agent 后应更新成功: %+v %v", result, err)
	}
	if got := server.agents(); !slices.Equal(got, []string{"sing-box", "clash.meta"}) {
		t.Fatalf("应先用默认标识、再换 clash.meta: %v", got)
	}
	metadata := loadFixtureMetadata(t, metaPath)
	if metadata.ResolvedUserAgent != "clash.meta" || metadata.UserAgent != "" {
		t.Fatalf("应记住自动识别结果且不改写用户设置: resolved=%q user=%q", metadata.ResolvedUserAgent, metadata.UserAgent)
	}
	if !hasDiagnostic(metadata, "subscription.user_agent_fallback") {
		t.Fatalf("应提示已自动换用 User-Agent: %+v", metadata.LastDiagnostics)
	}

	if _, err := runUpdate(t, root, time.Hour); err != nil {
		t.Fatalf("再次更新失败: %v", err)
	}
	if got := server.agents(); !slices.Equal(got, []string{"sing-box", "clash.meta", "clash.meta"}) {
		t.Fatalf("再次更新应直接使用记住的 User-Agent: %v", got)
	}
}

// 记住的 User-Agent 失效后回到默认标识，并清掉记忆。
func TestUpdateRetriesDefaultWhenRememberedUserAgentStopsWorking(t *testing.T) {
	server := newUserAgentServer(t, func(userAgent string, writer http.ResponseWriter) {
		if userAgent == "sing-box" {
			_, _ = writer.Write([]byte(boxWithOneNode))
			return
		}
		_, _ = writer.Write([]byte(templateWithoutNodes))
	})
	root, metaPath := userAgentFixture(t, server.URL, func(metadata *catalog.Metadata) {
		metadata.ResolvedUserAgent = "clash.meta"
	})

	if _, err := runUpdate(t, root, 0); err != nil {
		t.Fatalf("记住的标识失效后应回到默认标识: %v", err)
	}
	if got := server.agents(); !slices.Equal(got, []string{"clash.meta", "sing-box"}) {
		t.Fatalf("应先用记住的标识、再回到默认: %v", got)
	}
	if resolved := loadFixtureMetadata(t, metaPath).ResolvedUserAgent; resolved != "" {
		t.Fatalf("默认标识成功后应清掉记忆: %q", resolved)
	}
}

// 用户手填了 User-Agent 就只用它，不自动替换。
func TestUpdateKeepsExplicitUserAgent(t *testing.T) {
	server := newUserAgentServer(t, func(_ string, writer http.ResponseWriter) {
		_, _ = writer.Write([]byte(templateWithoutNodes))
	})
	root, metaPath := userAgentFixture(t, server.URL, func(metadata *catalog.Metadata) {
		metadata.UserAgent = "my-agent"
	})

	if _, err := runUpdate(t, root, 0); err == nil {
		t.Fatal("没有节点的订阅应更新失败")
	}
	if got := server.agents(); !slices.Equal(got, []string{"my-agent"}) {
		t.Fatalf("用户手填 User-Agent 时不应换用其他标识: %v", got)
	}
	if metadata := loadFixtureMetadata(t, metaPath); metadata.ResolvedUserAgent != "" || metadata.UserAgent != "my-agent" {
		t.Fatalf("不应改写用户设置: %+v", metadata)
	}
}

// 所有标识都取不到节点时失败，并留下说明。
func TestUpdateReportsExhaustedUserAgents(t *testing.T) {
	server := newUserAgentServer(t, func(_ string, writer http.ResponseWriter) {
		_, _ = writer.Write([]byte(templateWithoutNodes))
	})
	root, metaPath := userAgentFixture(t, server.URL, nil)

	if _, err := runUpdate(t, root, 0); err == nil {
		t.Fatal("所有标识都没有节点时应更新失败")
	}
	if got := server.agents(); !slices.Equal(got, []string{"sing-box", "clash.meta", "v2rayN", "Shadowrocket"}) {
		t.Fatalf("应依次尝试全部备选标识: %v", got)
	}
	metadata := loadFixtureMetadata(t, metaPath)
	if metadata.ResolvedUserAgent != "" || !hasDiagnostic(metadata, "subscription.user_agent_exhausted") {
		t.Fatalf("应记录已尝试全部标识: %+v", metadata)
	}
}

// 默认标识被服务端 403 拒绝时同样换用其他标识。
func TestUpdateFallsBackWhenDefaultUserAgentIsForbidden(t *testing.T) {
	server := newUserAgentServer(t, func(userAgent string, writer http.ResponseWriter) {
		if userAgent == "sing-box" {
			writer.WriteHeader(http.StatusForbidden)
			return
		}
		_, _ = writer.Write([]byte(clashWithOneNode))
	})
	root, metaPath := userAgentFixture(t, server.URL, nil)

	if result, err := runUpdate(t, root, 0); err != nil || result.NodeCount != 1 {
		t.Fatalf("403 后应换用其他标识更新成功: %+v %v", result, err)
	}
	if resolved := loadFixtureMetadata(t, metaPath).ResolvedUserAgent; resolved != "clash.meta" {
		t.Fatalf("应记住成功的标识: %q", resolved)
	}
}

// 401 多半是令牌错误，换 User-Agent 没有意义。
func TestUpdateDoesNotRetryUserAgentOnUnauthorized(t *testing.T) {
	server := newUserAgentServer(t, func(_ string, writer http.ResponseWriter) {
		writer.WriteHeader(http.StatusUnauthorized)
	})
	root, _ := userAgentFixture(t, server.URL, nil)

	if _, err := runUpdate(t, root, 0); err == nil {
		t.Fatal("401 应更新失败")
	}
	if got := server.agents(); !slices.Equal(got, []string{"sing-box"}) {
		t.Fatalf("401 不应换用其他标识重试: %v", got)
	}
}

// 修改订阅地址后，之前自动识别的结果不再适用：下一次请求从默认标识重新开始。
func TestEditResetsResolvedUserAgentWhenURLChanges(t *testing.T) {
	server := newUserAgentServer(t, func(userAgent string, writer http.ResponseWriter) {
		if userAgent == "clash.meta" {
			_, _ = writer.Write([]byte(clashWithOneNode))
			return
		}
		_, _ = writer.Write([]byte(templateWithoutNodes))
	})
	root, metaPath := userAgentFixture(t, server.URL, func(metadata *catalog.Metadata) {
		metadata.ResolvedUserAgent = "clash.meta"
	})
	newURL := server.URL + "/other"
	if _, err := Edit(t.Context(), EditOptions{Root: root, GroupID: "fixture", URL: &newURL, Now: time.Unix(1700000000, 0)}); err != nil {
		t.Fatal(err)
	}
	if got := server.agents(); !slices.Equal(got, []string{"sing-box", "clash.meta"}) {
		t.Fatalf("修改地址后应从默认标识重新识别: %v", got)
	}
	if resolved := loadFixtureMetadata(t, metaPath).ResolvedUserAgent; resolved != "clash.meta" {
		t.Fatalf("重新识别后应记住新的结果: %q", resolved)
	}
}
