package catalog

import "github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/region"

// RegionKey 返回与语言无关的稳定地区标识，供节点列表按地区展示；无法识别时返回 other。
func RegionKey(tag string) string {
	if detected, ok := region.Detect(tag); ok {
		return detected.Key
	}
	return "other"
}
