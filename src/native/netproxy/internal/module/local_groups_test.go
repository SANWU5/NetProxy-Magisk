package module

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/catalog"
)

func TestNodeAppendContentCreatesNamedLocalGroup(t *testing.T) {
	options := selectionFixture(t)
	result, err := NodeAppendContent(t.Context(), options, "", "家里", []byte("socks://example.test:1080#HOME"), false)
	if err != nil || result.GroupID == "default" || result.NodeCount != 1 {
		t.Fatal("新建分组导入结果错误", result, err)
	}
	if typ, err := catalog.GroupType(t.Context(), options.CatalogRoot, result.GroupID); err != nil || typ != "local" {
		t.Fatal("新分组不是本地分组", typ, err)
	}
	if id, err := catalog.ResolveGroup(t.Context(), options.CatalogRoot, "家里"); err != nil || id != result.GroupID {
		t.Fatal("新分组名称无法解析", id, err)
	}
	if _, err := NodeAppendContent(t.Context(), options, "", "家里", []byte("socks://example.test:1081#AGAIN"), false); err == nil {
		t.Fatal("允许了同名分组")
	}
	if _, err := NodeAppendContent(t.Context(), options, "", "本地配置", []byte("socks://example.test:1081#AGAIN"), false); err == nil {
		t.Fatal("允许了与 default 同名的分组")
	}
	before, err := os.ReadDir(options.CatalogRoot)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := NodeAppendContent(t.Context(), options, "", "坏内容", []byte("not a node"), false); err == nil {
		t.Fatal("无法解析的内容被接受")
	}
	after, err := os.ReadDir(options.CatalogRoot)
	if err != nil || len(after) != len(before) {
		t.Fatal("解析失败后留下了空分组", len(before), len(after), err)
	}
}

func TestNodeAppendContentRejectsSubscriptionGroups(t *testing.T) {
	options := selectionFixture(t)
	if err := catalog.InitializeGroup(t.Context(), catalog.GroupOptions{
		Root: options.CatalogRoot, GroupID: "sub", Name: "机场", Type: "subscription", URL: "https://example.test/sub",
	}); err != nil {
		t.Fatal(err)
	}
	if _, err := NodeAppendContent(t.Context(), options, "sub", "", []byte("socks://example.test:1080#X"), false); err == nil {
		t.Fatal("允许把节点加入 URL 订阅")
	}
}

func TestRemoveSubscriptionDeletesLocalGroupsAndClearsDefault(t *testing.T) {
	options := selectionFixture(t)
	created, err := NodeAppendContent(t.Context(), options, "", "临时", []byte("socks://example.test:1080#TMP"), false)
	if err != nil {
		t.Fatal(err)
	}
	if err := RemoveSubscription(t.Context(), options, created.GroupID, ""); err != nil {
		t.Fatal("删除本地分组失败", err)
	}
	if _, err := os.Stat(filepath.Join(options.CatalogRoot, created.GroupID)); !os.IsNotExist(err) {
		t.Fatal("本地分组没有删除", err)
	}
	if err := RemoveSubscription(t.Context(), options, "default", ""); err != nil {
		t.Fatal("清空本地配置失败", err)
	}
	document, err := catalog.GroupProvider(t.Context(), options.CatalogRoot, "default")
	if err != nil || len(document.Outbounds)+len(document.Endpoints) != 0 {
		t.Fatal("本地配置没有清空", err)
	}
	if _, err := os.Stat(filepath.Join(options.CatalogRoot, "default", "meta.json")); err != nil {
		t.Fatal("本地配置分组被删除", err)
	}
}
