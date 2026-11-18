package region

import "testing"

func TestDetectRecognizesCommonNodeNames(t *testing.T) {
	cases := map[string]string{
		"🇺🇸美国洛杉矶08 | 三网推荐":      "us",
		"🇸🇬AWS新加坡01 | 电信移动联通推荐": "sg",
		"HK01":           "hk",
		"香港 IPLC 02":     "hk",
		"上海-香港 IPLC":     "hk",
		"沪日 01 | 1.5x":   "jp",
		"印度尼西亚 01":       "id",
		"印度 孟买":          "in",
		"Los Angeles 02": "us",
		"Japan-Tokyo":    "jp",
		"TW-01":          "tw",
		"澳门01":           "mo",
		"澳洲 悉尼":          "au",
		"Russia Moscow":  "ru",
		"回国 01":          "cn",
		"深港专线 03":        "hk",
	}
	for tag, want := range cases {
		got, ok := Detect(tag)
		if !ok || got.Key != want {
			t.Fatalf("Detect(%q) = %q, %v; want %q", tag, got.Key, ok, want)
		}
	}
}

func TestDetectSkipsInformationalAndAmbiguousNames(t *testing.T) {
	for _, tag := range []string{"剩余流量：100 GB", "套餐到期：2026-12-01", "官网 example.com", "plus 节点", "节点 1", "Premium"} {
		if got, ok := Detect(tag); ok {
			t.Fatalf("Detect(%q) = %q; want no region", tag, got.Key)
		}
	}
}

func TestLookupAndFlag(t *testing.T) {
	us := Lookup("US")
	if us.Key != "us" || us.Flag != "🇺🇸" || us.Label(false) != "🇺🇸 美国" || us.Label(true) != "🇺🇸 United States" {
		t.Fatalf("Lookup(US) = %+v", us)
	}
	unknown := Lookup("zz")
	if unknown.Label(false) != "🇿🇿 ZZ" {
		t.Fatalf("unknown label = %q", unknown.Label(false))
	}
}
